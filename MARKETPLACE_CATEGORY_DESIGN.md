# Marketplace Category Structure - Design Documentation

## Document Information
- **Version**: 1.0
- **Date**: 2026-02-10
- **Author**: Content Service Team
- **User Story**: [KB-12838](https://karmayogibharat.atlassian.net/browse/KB-12838)
- **Figma Design**: [Marketplace Design](https://figma.com/design/bloj5l1lZIm2oIyDxf1Jr1/Marketplace?node-id=3103-5181&t=HtvdbrkH39DbvsyU-0)

## Table of Contents
1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Data Model Design](#data-model-design)
4. [PostgreSQL Schema](#postgresql-schema)
5. [Elasticsearch Schema](#elasticsearch-schema)
6. [API Specifications](#api-specifications)
7. [Integration Patterns](#integration-patterns)
8. [Sample Queries](#sample-queries)
9. [Migration Strategy](#migration-strategy)
10. [Security Considerations](#security-considerations)
11. [Performance Optimization](#performance-optimization)
12. [Implementation Guidelines](#implementation-guidelines)

---

## 1. Overview

### 1.1 Purpose
This document provides the technical design for implementing a hierarchical marketplace category structure in the CIOS Content Service. The structure supports a three-level hierarchy:
- **Category** (Level 1)
- **SubCategory** (Level 2)
- **Articles** (Level 3 - nested within SubCategory)

### 1.2 Key Requirements
- Support hierarchical content organization (Category → SubCategory → Articles)
- Store data in both PostgreSQL (using JSONB) and Elasticsearch
- Enable efficient search and retrieval operations
- Support marketplace SSO integration and partner registration
- Track external course progress updates
- Maintain data consistency across storage systems

### 1.3 Technical Stack
- **Database**: PostgreSQL 12+ (with JSONB support)
- **Search Engine**: Elasticsearch 7.x/8.x
- **Data Format**: JSON/JSONB for flexible schema

---

## 2. System Architecture

### 2.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        API Layer                            │
│  (REST/GraphQL endpoints for Category/SubCategory/Article)  │
└────────────┬────────────────────────────────────┬───────────┘
             │                                    │
             ▼                                    ▼
┌────────────────────────┐          ┌────────────────────────┐
│   PostgreSQL Database  │          │    Elasticsearch       │
│   (Source of Truth)    │◄────────►│   (Search & Cache)     │
│                        │   Sync   │                        │
│   - Categories Table   │          │   - categories Index   │
│   - JSONB for nested   │          │   - Nested Documents   │
│     subcategories      │          │   - Full-text Search   │
└────────────────────────┘          └────────────────────────┘
```

### 2.2 Data Flow

1. **Write Operations**: 
   - Data written to PostgreSQL first (source of truth)
   - Async sync to Elasticsearch for search optimization

2. **Read Operations**:
   - Simple lookups: PostgreSQL (by ID)
   - Search/Filter operations: Elasticsearch
   - Complex queries: PostgreSQL with JSONB operators

3. **Consistency**:
   - Event-driven sync mechanism (CDC or message queue)
   - Periodic reconciliation jobs

---

## 3. Data Model Design

### 3.1 Conceptual Model

```
Category (Root)
├── id: UUID
├── name: String
├── description: String
├── metadata: Object
├── subCategories: Array [
│   ├── SubCategory
│   │   ├── id: UUID
│   │   ├── name: String
│   │   ├── description: String
│   │   ├── metadata: Object
│   │   └── articles: Array [
│   │       ├── Article
│   │       │   ├── id: UUID
│   │       │   ├── title: String
│   │       │   ├── content: Text
│   │       │   ├── author: Object
│   │       │   ├── tags: Array
│   │       │   ├── status: Enum
│   │       │   ├── metadata: Object
│   │       │   └── timestamps
│   │       └── ...
│   │   ]
│   └── ...
└── timestamps
```

### 3.2 Entity Relationships

```
┌──────────────┐
│   Category   │
│              │
│ - id (PK)    │
│ - name       │
│ - slug       │
│ - metadata   │
└──────┬───────┘
       │
       │ has many (JSONB nested)
       │
       ▼
┌──────────────┐
│ SubCategory  │
│              │
│ - id         │
│ - name       │
│ - slug       │
│ - metadata   │
└──────┬───────┘
       │
       │ contains many (JSONB nested)
       │
       ▼
┌──────────────┐
│   Article    │
│              │
│ - id         │
│ - title      │
│ - content    │
│ - author     │
│ - tags       │
│ - status     │
└──────────────┘
```

---

## 4. PostgreSQL Schema

### 4.1 Categories Table

```sql
-- Main categories table with nested JSONB structure
CREATE TABLE marketplace_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    icon_url VARCHAR(500),
    display_order INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'archived')),
    
    -- Nested subcategories and articles in JSONB
    sub_categories JSONB NOT NULL DEFAULT '[]'::jsonb,
    
    -- Metadata for extensibility
    metadata JSONB DEFAULT '{}'::jsonb,
    
    -- Partner information (for marketplace integration)
    partner_info JSONB DEFAULT '{}'::jsonb,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    
    -- Soft delete support
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    -- Full-text search support
    search_vector TSVECTOR
);

-- Indexes for performance
CREATE INDEX idx_categories_slug ON marketplace_categories(slug);
CREATE INDEX idx_categories_status ON marketplace_categories(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_display_order ON marketplace_categories(display_order);
CREATE INDEX idx_categories_created_at ON marketplace_categories(created_at DESC);

-- JSONB indexes for nested queries
CREATE INDEX idx_categories_subcategories ON marketplace_categories USING GIN (sub_categories);
CREATE INDEX idx_categories_metadata ON marketplace_categories USING GIN (metadata);
CREATE INDEX idx_categories_partner_info ON marketplace_categories USING GIN (partner_info);

-- Full-text search index
CREATE INDEX idx_categories_search ON marketplace_categories USING GIN (search_vector);

-- Trigger to update search_vector
CREATE OR REPLACE FUNCTION update_categories_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('english', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.sub_categories::text, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_categories_search_vector
    BEFORE INSERT OR UPDATE ON marketplace_categories
    FOR EACH ROW
    EXECUTE FUNCTION update_categories_search_vector();

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_categories_updated_at
    BEFORE UPDATE ON marketplace_categories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

### 4.2 JSONB Structure for SubCategories and Articles

```json
{
  "sub_categories": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Web Development",
      "slug": "web-development",
      "description": "Learn web development fundamentals",
      "icon_url": "https://cdn.example.com/icons/web-dev.png",
      "display_order": 1,
      "metadata": {
        "difficulty_level": "beginner",
        "estimated_duration": "3 months",
        "prerequisites": []
      },
      "articles": [
        {
          "id": "660e8400-e29b-41d4-a716-446655440002",
          "title": "Introduction to HTML",
          "slug": "introduction-to-html",
          "content_type": "tutorial",
          "summary": "Learn the basics of HTML markup",
          "content_url": "https://content.example.com/articles/intro-html",
          "thumbnail_url": "https://cdn.example.com/thumbs/html.jpg",
          "author": {
            "id": "770e8400-e29b-41d4-a716-446655440003",
            "name": "John Doe",
            "email": "john@example.com",
            "avatar_url": "https://cdn.example.com/avatars/john.jpg"
          },
          "tags": ["html", "beginner", "web-basics"],
          "status": "published",
          "visibility": "public",
          "language": "en",
          "reading_time_minutes": 15,
          "difficulty_level": "beginner",
          
          "external_course_info": {
            "is_external": false,
            "provider": null,
            "course_id": null,
            "enrollment_url": null,
            "progress_tracking_enabled": false
          },
          
          "engagement_metrics": {
            "views": 1250,
            "likes": 98,
            "bookmarks": 45,
            "completions": 780,
            "average_rating": 4.5,
            "total_ratings": 123
          },
          
          "seo_metadata": {
            "meta_title": "Introduction to HTML - Complete Guide",
            "meta_description": "Learn HTML from scratch with this comprehensive guide",
            "keywords": ["html tutorial", "learn html", "html basics"]
          },
          
          "published_at": "2024-01-15T10:00:00Z",
          "updated_at": "2024-02-01T14:30:00Z",
          "created_at": "2024-01-10T09:00:00Z"
        }
      ]
    }
  ]
}
```

### 4.3 Supporting Tables

```sql
-- Table for tracking external course progress
CREATE TABLE external_course_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES marketplace_categories(id),
    subcategory_id UUID NOT NULL,
    article_id UUID NOT NULL,
    
    -- Progress tracking
    status VARCHAR(20) DEFAULT 'not_started' CHECK (status IN ('not_started', 'in_progress', 'completed', 'abandoned')),
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    
    -- External course specific
    external_provider VARCHAR(100),
    external_course_id VARCHAR(255),
    enrollment_date TIMESTAMP WITH TIME ZONE,
    completion_date TIMESTAMP WITH TIME ZONE,
    certificate_url VARCHAR(500),
    
    -- Timestamps
    started_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_user_article UNIQUE (user_id, article_id)
);

-- Indexes
CREATE INDEX idx_course_progress_user ON external_course_progress(user_id);
CREATE INDEX idx_course_progress_category ON external_course_progress(category_id);
CREATE INDEX idx_course_progress_status ON external_course_progress(status);
CREATE INDEX idx_course_progress_last_accessed ON external_course_progress(last_accessed_at DESC);

-- Partner registration table (for marketplace SSO)
CREATE TABLE marketplace_partners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    logo_url VARCHAR(500),
    website_url VARCHAR(500),
    
    -- SSO Configuration
    sso_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    
    -- API Configuration
    api_config JSONB DEFAULT '{}'::jsonb,
    
    -- Partner status
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'suspended', 'inactive')),
    
    -- Categories this partner provides content for
    category_ids UUID[] DEFAULT ARRAY[]::UUID[],
    
    -- Contact information
    contact_info JSONB DEFAULT '{}'::jsonb,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID
);

-- Indexes
CREATE INDEX idx_partners_slug ON marketplace_partners(slug);
CREATE INDEX idx_partners_status ON marketplace_partners(status);
CREATE INDEX idx_partners_category_ids ON marketplace_partners USING GIN (category_ids);
CREATE INDEX idx_partners_sso_config ON marketplace_partners USING GIN (sso_config);

-- Table for tracking article views and engagement
CREATE TABLE article_engagement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES marketplace_categories(id),
    subcategory_id UUID NOT NULL,
    article_id UUID NOT NULL,
    user_id UUID,
    session_id VARCHAR(255),
    
    -- Engagement type
    engagement_type VARCHAR(50) NOT NULL CHECK (engagement_type IN ('view', 'like', 'bookmark', 'share', 'complete', 'rate')),
    
    -- Additional data
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    metadata JSONB DEFAULT '{}'::jsonb,
    
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- IP and user agent for analytics
    ip_address INET,
    user_agent TEXT
);

-- Indexes
CREATE INDEX idx_engagement_category ON article_engagement(category_id);
CREATE INDEX idx_engagement_article ON article_engagement(article_id);
CREATE INDEX idx_engagement_user ON article_engagement(user_id);
CREATE INDEX idx_engagement_type ON article_engagement(engagement_type);
CREATE INDEX idx_engagement_created_at ON article_engagement(created_at DESC);
```

---

## 5. Elasticsearch Schema

### 5.1 Index Mapping

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2,
    "analysis": {
      "analyzer": {
        "custom_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding", "stop", "snowball"]
        },
        "autocomplete_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "edge_ngram_filter"]
        }
      },
      "filter": {
        "edge_ngram_filter": {
          "type": "edge_ngram",
          "min_gram": 2,
          "max_gram": 20
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "name": {
        "type": "text",
        "analyzer": "custom_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          },
          "autocomplete": {
            "type": "text",
            "analyzer": "autocomplete_analyzer"
          }
        }
      },
      "slug": {
        "type": "keyword"
      },
      "description": {
        "type": "text",
        "analyzer": "custom_analyzer"
      },
      "icon_url": {
        "type": "keyword",
        "index": false
      },
      "display_order": {
        "type": "integer"
      },
      "status": {
        "type": "keyword"
      },
      "sub_categories": {
        "type": "nested",
        "properties": {
          "id": {
            "type": "keyword"
          },
          "name": {
            "type": "text",
            "analyzer": "custom_analyzer",
            "fields": {
              "keyword": {
                "type": "keyword"
              },
              "autocomplete": {
                "type": "text",
                "analyzer": "autocomplete_analyzer"
              }
            }
          },
          "slug": {
            "type": "keyword"
          },
          "description": {
            "type": "text",
            "analyzer": "custom_analyzer"
          },
          "icon_url": {
            "type": "keyword",
            "index": false
          },
          "display_order": {
            "type": "integer"
          },
          "metadata": {
            "type": "object",
            "enabled": true,
            "properties": {
              "difficulty_level": {
                "type": "keyword"
              },
              "estimated_duration": {
                "type": "keyword"
              },
              "prerequisites": {
                "type": "keyword"
              }
            }
          },
          "articles": {
            "type": "nested",
            "properties": {
              "id": {
                "type": "keyword"
              },
              "title": {
                "type": "text",
                "analyzer": "custom_analyzer",
                "fields": {
                  "keyword": {
                    "type": "keyword"
                  },
                  "autocomplete": {
                    "type": "text",
                    "analyzer": "autocomplete_analyzer"
                  }
                }
              },
              "slug": {
                "type": "keyword"
              },
              "content_type": {
                "type": "keyword"
              },
              "summary": {
                "type": "text",
                "analyzer": "custom_analyzer"
              },
              "content_url": {
                "type": "keyword",
                "index": false
              },
              "thumbnail_url": {
                "type": "keyword",
                "index": false
              },
              "author": {
                "type": "object",
                "properties": {
                  "id": {
                    "type": "keyword"
                  },
                  "name": {
                    "type": "text",
                    "fields": {
                      "keyword": {
                        "type": "keyword"
                      }
                    }
                  },
                  "email": {
                    "type": "keyword"
                  }
                }
              },
              "tags": {
                "type": "keyword"
              },
              "status": {
                "type": "keyword"
              },
              "visibility": {
                "type": "keyword"
              },
              "language": {
                "type": "keyword"
              },
              "reading_time_minutes": {
                "type": "integer"
              },
              "difficulty_level": {
                "type": "keyword"
              },
              "external_course_info": {
                "type": "object",
                "properties": {
                  "is_external": {
                    "type": "boolean"
                  },
                  "provider": {
                    "type": "keyword"
                  },
                  "course_id": {
                    "type": "keyword"
                  },
                  "progress_tracking_enabled": {
                    "type": "boolean"
                  }
                }
              },
              "engagement_metrics": {
                "type": "object",
                "properties": {
                  "views": {
                    "type": "integer"
                  },
                  "likes": {
                    "type": "integer"
                  },
                  "bookmarks": {
                    "type": "integer"
                  },
                  "completions": {
                    "type": "integer"
                  },
                  "average_rating": {
                    "type": "float"
                  },
                  "total_ratings": {
                    "type": "integer"
                  }
                }
              },
              "seo_metadata": {
                "type": "object",
                "enabled": false
              },
              "published_at": {
                "type": "date"
              },
              "updated_at": {
                "type": "date"
              },
              "created_at": {
                "type": "date"
              }
            }
          }
        }
      },
      "metadata": {
        "type": "object",
        "enabled": true
      },
      "partner_info": {
        "type": "object",
        "enabled": true
      },
      "created_at": {
        "type": "date"
      },
      "updated_at": {
        "type": "date"
      }
    }
  }
}
```

### 5.2 Index Creation Command

```bash
# Create the marketplace_categories index
PUT /marketplace_categories
{
  # Use the mapping from section 5.1
}
```

### 5.3 Sample Elasticsearch Document

```json
{
  "id": "450e8400-e29b-41d4-a716-446655440000",
  "name": "Technology",
  "slug": "technology",
  "description": "Explore the latest in technology and innovation",
  "icon_url": "https://cdn.example.com/icons/technology.png",
  "display_order": 1,
  "status": "active",
  "sub_categories": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Web Development",
      "slug": "web-development",
      "description": "Learn web development fundamentals",
      "display_order": 1,
      "metadata": {
        "difficulty_level": "beginner",
        "estimated_duration": "3 months"
      },
      "articles": [
        {
          "id": "660e8400-e29b-41d4-a716-446655440002",
          "title": "Introduction to HTML",
          "slug": "introduction-to-html",
          "content_type": "tutorial",
          "summary": "Learn the basics of HTML markup",
          "tags": ["html", "beginner", "web-basics"],
          "status": "published",
          "difficulty_level": "beginner",
          "published_at": "2024-01-15T10:00:00Z"
        }
      ]
    }
  ],
  "created_at": "2024-01-01T00:00:00Z",
  "updated_at": "2024-02-01T00:00:00Z"
}
```

---

## 6. API Specifications

### 6.1 Category APIs

#### 6.1.1 Create Category

```http
POST /api/v1/marketplace/categories
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Technology",
  "slug": "technology",
  "description": "Explore the latest in technology",
  "icon_url": "https://cdn.example.com/icons/tech.png",
  "display_order": 1,
  "metadata": {
    "featured": true,
    "color_theme": "#0066CC"
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "450e8400-e29b-41d4-a716-446655440000",
    "name": "Technology",
    "slug": "technology",
    "sub_categories": [],
    "created_at": "2024-02-10T10:00:00Z"
  }
}
```

#### 6.1.2 Get All Categories

```http
GET /api/v1/marketplace/categories?page=1&limit=20&status=active
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "450e8400-e29b-41d4-a716-446655440000",
      "name": "Technology",
      "slug": "technology",
      "description": "Explore the latest in technology",
      "sub_categories_count": 5,
      "articles_count": 23,
      "created_at": "2024-01-01T00:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 50,
    "total_pages": 3
  }
}
```

#### 6.1.3 Get Category by ID/Slug

```http
GET /api/v1/marketplace/categories/{id_or_slug}?include=subcategories,articles
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "450e8400-e29b-41d4-a716-446655440000",
    "name": "Technology",
    "slug": "technology",
    "description": "Explore the latest in technology",
    "sub_categories": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "name": "Web Development",
        "articles_count": 15
      }
    ]
  }
}
```

#### 6.1.4 Update Category

```http
PUT /api/v1/marketplace/categories/{id}
Content-Type: application/json
Authorization: Bearer {token}

{
  "description": "Updated description",
  "display_order": 2
}

Response: 200 OK
```

#### 6.1.5 Delete Category

```http
DELETE /api/v1/marketplace/categories/{id}
Authorization: Bearer {token}

Response: 204 No Content
```

### 6.2 SubCategory APIs

#### 6.2.1 Add SubCategory to Category

```http
POST /api/v1/marketplace/categories/{category_id}/subcategories
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Web Development",
  "slug": "web-development",
  "description": "Learn web development",
  "display_order": 1,
  "metadata": {
    "difficulty_level": "beginner",
    "estimated_duration": "3 months"
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "name": "Web Development",
    "category_id": "450e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### 6.2.2 Get SubCategories of a Category

```http
GET /api/v1/marketplace/categories/{category_id}/subcategories
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Web Development",
      "articles_count": 15
    }
  ]
}
```

#### 6.2.3 Update SubCategory

```http
PUT /api/v1/marketplace/categories/{category_id}/subcategories/{subcategory_id}
Content-Type: application/json
Authorization: Bearer {token}

{
  "description": "Updated description"
}

Response: 200 OK
```

#### 6.2.4 Delete SubCategory

```http
DELETE /api/v1/marketplace/categories/{category_id}/subcategories/{subcategory_id}
Authorization: Bearer {token}

Response: 204 No Content
```

### 6.3 Article APIs

#### 6.3.1 Add Article to SubCategory

```http
POST /api/v1/marketplace/categories/{category_id}/subcategories/{subcategory_id}/articles
Content-Type: application/json
Authorization: Bearer {token}

{
  "title": "Introduction to HTML",
  "slug": "introduction-to-html",
  "content_type": "tutorial",
  "summary": "Learn HTML basics",
  "content_url": "https://content.example.com/articles/intro-html",
  "tags": ["html", "beginner"],
  "status": "published",
  "difficulty_level": "beginner",
  "reading_time_minutes": 15,
  "external_course_info": {
    "is_external": false
  }
}

Response: 201 Created
```

#### 6.3.2 Get Articles in SubCategory

```http
GET /api/v1/marketplace/categories/{category_id}/subcategories/{subcategory_id}/articles
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440002",
      "title": "Introduction to HTML",
      "summary": "Learn HTML basics",
      "tags": ["html", "beginner"]
    }
  ]
}
```

#### 6.3.3 Get Article Details

```http
GET /api/v1/marketplace/articles/{article_id}
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440002",
    "title": "Introduction to HTML",
    "content_url": "https://content.example.com/articles/intro-html",
    "author": {
      "name": "John Doe"
    },
    "engagement_metrics": {
      "views": 1250,
      "likes": 98
    }
  }
}
```

#### 6.3.4 Update Article

```http
PUT /api/v1/marketplace/articles/{article_id}
Content-Type: application/json
Authorization: Bearer {token}

{
  "title": "Updated Title"
}

Response: 200 OK
```

#### 6.3.5 Delete Article

```http
DELETE /api/v1/marketplace/articles/{article_id}
Authorization: Bearer {token}

Response: 204 No Content
```

### 6.4 Search APIs

#### 6.4.1 Global Search

```http
GET /api/v1/marketplace/search?q=web+development&type=all&page=1&limit=20
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "categories": [...],
    "subcategories": [...],
    "articles": [...]
  },
  "total_results": 45
}
```

#### 6.4.2 Search Articles

```http
GET /api/v1/marketplace/search/articles?q=html&difficulty=beginner&tags=web-basics
Authorization: Bearer {token}

Response: 200 OK
```

### 6.5 Progress Tracking APIs

#### 6.5.1 Update Course Progress

```http
POST /api/v1/marketplace/progress
Content-Type: application/json
Authorization: Bearer {token}

{
  "article_id": "660e8400-e29b-41d4-a716-446655440002",
  "status": "in_progress",
  "progress_percentage": 45
}

Response: 200 OK
```

#### 6.5.2 Get User Progress

```http
GET /api/v1/marketplace/progress?user_id={user_id}
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "article_id": "660e8400-e29b-41d4-a716-446655440002",
      "status": "in_progress",
      "progress_percentage": 45,
      "last_accessed_at": "2024-02-10T10:00:00Z"
    }
  ]
}
```

---

## 7. Integration Patterns

### 7.1 Marketplace SSO Integration

#### 7.1.1 Partner SSO Configuration

```json
{
  "sso_config": {
    "provider": "oauth2",
    "auth_endpoint": "https://partner.example.com/oauth/authorize",
    "token_endpoint": "https://partner.example.com/oauth/token",
    "userinfo_endpoint": "https://partner.example.com/oauth/userinfo",
    "client_id": "marketplace_client_id",
    "client_secret_ref": "vault://secrets/partner_client_secret",
    "scopes": ["openid", "profile", "email"],
    "redirect_uri": "https://marketplace.example.com/auth/callback",
    "token_validation": {
      "jwks_uri": "https://partner.example.com/.well-known/jwks.json",
      "issuer": "https://partner.example.com",
      "audience": "marketplace"
    }
  }
}
```

#### 7.1.2 SSO Flow

```
User → Marketplace → Partner SSO → Authenticate → Callback → Create/Link User → Access Content
```

### 7.2 Partner Registration

#### 7.2.1 Registration Workflow

1. Partner submits registration request via API/Portal
2. System validates partner information
3. Administrator reviews and approves
4. SSO configuration is set up
5. Partner receives API credentials
6. Partner can start publishing content

#### 7.2.2 Partner API Authentication

```http
POST /api/v1/partners/auth/token
Content-Type: application/json

{
  "client_id": "partner_client_id",
  "client_secret": "partner_client_secret",
  "grant_type": "client_credentials"
}

Response: 200 OK
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 7.3 External Course Progress Integration

#### 7.3.1 Progress Webhook

Partners can send progress updates via webhook:

```http
POST /api/v1/webhooks/course-progress
Content-Type: application/json
Authorization: Bearer {partner_token}

{
  "user_id": "user_uuid",
  "article_id": "article_uuid",
  "progress_percentage": 75,
  "status": "in_progress",
  "metadata": {
    "last_module": "Module 5",
    "quiz_scores": [85, 90, 78]
  }
}
```

#### 7.3.2 Progress Polling

Marketplace can poll partner APIs for progress updates:

```http
GET https://partner.example.com/api/v1/courses/{course_id}/progress?user_id={user_id}
Authorization: Bearer {marketplace_token}

Response: 200 OK
{
  "course_id": "course_123",
  "user_id": "user_uuid",
  "progress_percentage": 75,
  "status": "in_progress",
  "last_accessed": "2024-02-10T10:00:00Z"
}
```

### 7.4 Data Synchronization

#### 7.4.1 PostgreSQL to Elasticsearch Sync

```python
# Pseudo-code for sync mechanism
def sync_category_to_elasticsearch(category_id):
    # 1. Fetch from PostgreSQL
    category = db.query(Category).get(category_id)
    
    # 2. Transform to ES document
    es_doc = transform_category_to_es_document(category)
    
    # 3. Index in Elasticsearch
    es.index(
        index='marketplace_categories',
        id=category.id,
        body=es_doc
    )
    
    # 4. Verify sync
    verify_sync(category_id)
```

#### 7.4.2 Event-Driven Sync

```python
# Listen to PostgreSQL changes
def on_category_change(event):
    if event.type == 'INSERT' or event.type == 'UPDATE':
        sync_category_to_elasticsearch(event.category_id)
    elif event.type == 'DELETE':
        es.delete(index='marketplace_categories', id=event.category_id)
```

---

## 8. Sample Queries

### 8.1 PostgreSQL JSONB Queries

#### 8.1.1 Find all subcategories in a category

```sql
SELECT 
    id,
    name,
    jsonb_array_length(sub_categories) as subcategory_count,
    sub_categories
FROM marketplace_categories
WHERE id = '450e8400-e29b-41d4-a716-446655440000';
```

#### 8.1.2 Search for articles by tag

```sql
SELECT 
    c.name as category_name,
    sc.value->>'name' as subcategory_name,
    a.value->>'title' as article_title,
    a.value->'tags' as tags
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE a.value->'tags' ? 'html'
    AND c.status = 'active'
    AND a.value->>'status' = 'published';
```

#### 8.1.3 Get all articles with specific difficulty level

```sql
SELECT 
    c.id as category_id,
    c.name as category_name,
    sc.value->>'id' as subcategory_id,
    sc.value->>'name' as subcategory_name,
    a.value->>'id' as article_id,
    a.value->>'title' as article_title,
    a.value->>'difficulty_level' as difficulty
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE a.value->>'difficulty_level' = 'beginner'
    AND a.value->>'status' = 'published';
```

#### 8.1.4 Count articles per category

```sql
SELECT 
    c.id,
    c.name,
    COUNT(*) as total_articles
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE c.status = 'active'
GROUP BY c.id, c.name
ORDER BY total_articles DESC;
```

#### 8.1.5 Find external courses

```sql
SELECT 
    c.name as category_name,
    sc.value->>'name' as subcategory_name,
    a.value->>'title' as article_title,
    a.value->'external_course_info'->>'provider' as provider
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE (a.value->'external_course_info'->>'is_external')::boolean = true;
```

#### 8.1.6 Update article status in JSONB

```sql
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles, 0, status}',
    '"published"'
)
WHERE id = '450e8400-e29b-41d4-a716-446655440000';
```

#### 8.1.7 Add new article to subcategory

```sql
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles}',
    (sub_categories->0->'articles') || 
    '[{"id": "new-uuid", "title": "New Article", "status": "draft"}]'::jsonb
)
WHERE id = '450e8400-e29b-41d4-a716-446655440000';
```

#### 8.1.8 Full-text search

```sql
SELECT 
    id,
    name,
    description,
    ts_rank(search_vector, query) as rank
FROM marketplace_categories,
    to_tsquery('english', 'web & development') query
WHERE search_vector @@ query
ORDER BY rank DESC
LIMIT 10;
```

### 8.2 Elasticsearch Queries

#### 8.2.1 Search articles by title

```json
GET /marketplace_categories/_search
{
  "query": {
    "nested": {
      "path": "sub_categories.articles",
      "query": {
        "match": {
          "sub_categories.articles.title": "HTML"
        }
      },
      "inner_hits": {
        "name": "matching_articles"
      }
    }
  }
}
```

#### 8.2.2 Filter by difficulty level

```json
GET /marketplace_categories/_search
{
  "query": {
    "nested": {
      "path": "sub_categories.articles",
      "query": {
        "bool": {
          "must": [
            {
              "term": {
                "sub_categories.articles.difficulty_level": "beginner"
              }
            },
            {
              "term": {
                "sub_categories.articles.status": "published"
              }
            }
          ]
        }
      }
    }
  }
}
```

#### 8.2.3 Autocomplete search

```json
GET /marketplace_categories/_search
{
  "query": {
    "multi_match": {
      "query": "web dev",
      "type": "bool_prefix",
      "fields": [
        "name.autocomplete",
        "sub_categories.name.autocomplete",
        "sub_categories.articles.title.autocomplete"
      ]
    }
  }
}
```

#### 8.2.4 Aggregation by category

```json
GET /marketplace_categories/_search
{
  "size": 0,
  "aggs": {
    "categories": {
      "terms": {
        "field": "name.keyword"
      },
      "aggs": {
        "subcategories": {
          "nested": {
            "path": "sub_categories"
          },
          "aggs": {
            "subcategory_names": {
              "terms": {
                "field": "sub_categories.name.keyword"
              }
            }
          }
        }
      }
    }
  }
}
```

#### 8.2.5 Search with filters

```json
GET /marketplace_categories/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "nested": {
            "path": "sub_categories.articles",
            "query": {
              "match": {
                "sub_categories.articles.title": "introduction"
              }
            }
          }
        }
      ],
      "filter": [
        {
          "term": {
            "status": "active"
          }
        },
        {
          "nested": {
            "path": "sub_categories.articles",
            "query": {
              "terms": {
                "sub_categories.articles.tags": ["beginner", "tutorial"]
              }
            }
          }
        }
      ]
    }
  }
}
```

---

## 9. Migration Strategy

### 9.1 Phase 1: Database Setup (Week 1)

1. Create PostgreSQL database and tables
2. Set up indexes and constraints
3. Create Elasticsearch cluster and index
4. Test basic CRUD operations

### 9.2 Phase 2: Data Migration (Week 2-3)

1. Identify existing data sources
2. Create data transformation scripts
3. Migrate categories first, then subcategories, then articles
4. Validate data integrity
5. Sync to Elasticsearch

### 9.3 Phase 3: API Implementation (Week 4-6)

1. Implement Category APIs
2. Implement SubCategory APIs
3. Implement Article APIs
4. Implement Search APIs
5. Add pagination and filtering

### 9.4 Phase 4: Integration (Week 7-8)

1. Implement SSO integration
2. Set up partner registration workflow
3. Implement progress tracking
4. Set up webhooks and polling

### 9.5 Phase 5: Testing & Optimization (Week 9-10)

1. Load testing
2. Performance optimization
3. Security audit
4. Documentation finalization

### 9.6 Rollback Plan

1. Keep old system running in parallel
2. Implement feature flags
3. Gradual traffic migration
4. Monitor error rates
5. Quick rollback capability

---

## 10. Security Considerations

### 10.1 Authentication & Authorization

- JWT-based authentication
- Role-based access control (RBAC)
- Partner-specific permissions
- API rate limiting

### 10.2 Data Protection

- Encryption at rest (PostgreSQL TDE)
- Encryption in transit (TLS 1.3)
- Sensitive data masking in logs
- GDPR compliance for user data

### 10.3 API Security

- Input validation and sanitization
- SQL injection prevention (parameterized queries)
- XSS prevention
- CSRF protection
- Request signing for partner APIs

### 10.4 Audit Logging

- Log all data modifications
- Track user actions
- Monitor API access
- Alert on suspicious activities

### 10.5 SSO Security

- Secure token storage
- Token expiration and rotation
- OAuth 2.0 / OpenID Connect best practices
- Partner SSO validation

---

## 11. Performance Optimization

### 11.1 Database Optimization

- Proper indexing strategy (shown in schema)
- JSONB indexing for nested queries
- Connection pooling
- Query optimization
- Materialized views for complex aggregations

### 11.2 Elasticsearch Optimization

- Shard allocation based on data size
- Replica configuration for availability
- Index lifecycle management
- Search query caching
- Bulk operations for data sync

### 11.3 Caching Strategy

- Redis for API response caching
- CDN for static content
- Browser caching headers
- Cache invalidation on updates

### 11.4 API Performance

- Pagination for large result sets
- Lazy loading for nested data
- GraphQL for flexible queries
- Response compression
- Async processing for heavy operations

### 11.5 Monitoring

- Query performance monitoring
- Slow query logging
- ES cluster health monitoring
- API response time tracking
- Error rate monitoring

---

## 12. Implementation Guidelines

### 12.1 Development Best Practices

1. **Version Control**: Use Git with feature branches
2. **Code Review**: Mandatory PR reviews
3. **Testing**: Unit tests, integration tests, E2E tests
4. **Documentation**: Inline code comments and API docs
5. **Linting**: Use ESLint/Prettier for code consistency

### 12.2 Database Conventions

1. Use UUIDs for primary keys
2. Always include audit fields (created_at, updated_at, created_by)
3. Implement soft deletes where applicable
4. Use JSONB for flexible nested structures
5. Create appropriate indexes before production

### 12.3 API Conventions

1. RESTful API design
2. Consistent error responses
3. API versioning (v1, v2, etc.)
4. Comprehensive error codes
5. OpenAPI/Swagger documentation

### 12.4 Error Handling

```json
{
  "success": false,
  "error": {
    "code": "CATEGORY_NOT_FOUND",
    "message": "Category with ID '123' not found",
    "details": {
      "category_id": "123"
    },
    "timestamp": "2024-02-10T10:00:00Z"
  }
}
```

### 12.5 Testing Strategy

1. **Unit Tests**: Test individual functions and methods
2. **Integration Tests**: Test API endpoints
3. **Database Tests**: Test JSONB queries and indexes
4. **Search Tests**: Test Elasticsearch queries
5. **Load Tests**: Simulate high traffic
6. **Security Tests**: Penetration testing

### 12.6 Deployment

1. Use Docker containers
2. Kubernetes for orchestration
3. Blue-green deployment
4. Database migrations with version control
5. Automated CI/CD pipelines

### 12.7 Monitoring & Alerting

1. Set up Prometheus/Grafana
2. Monitor database performance
3. Track Elasticsearch cluster health
4. API response time alerts
5. Error rate thresholds

---

## Appendix A: Glossary

- **Category**: Top-level classification in the marketplace
- **SubCategory**: Second-level classification under a category
- **Article**: Content item within a subcategory
- **JSONB**: PostgreSQL's binary JSON data type
- **SSO**: Single Sign-On authentication
- **CDC**: Change Data Capture

---

## Appendix B: References

- [User Story KB-12838](https://karmayogibharat.atlassian.net/browse/KB-12838)
- [Figma Design](https://figma.com/design/bloj5l1lZIm2oIyDxf1Jr1/Marketplace?node-id=3103-5181&t=HtvdbrkH39DbvsyU-0)
- [Marketplace Documentation](https://karmayogibharat.atlassian.net/wiki/spaces/TES/pages/605093889/Market+place+SSO+integration+and+partner+registration)
- [External Courses Progress](https://karmayogibharat.atlassian.net/wiki/spaces/TES/pages/413171733/External+courses+Progress+Update)
- PostgreSQL JSONB Documentation
- Elasticsearch Nested Objects Documentation

---

## Appendix C: Change Log

| Version | Date       | Author | Changes                     |
|---------|------------|--------|-----------------------------|
| 1.0     | 2024-02-10 | Team   | Initial design document     |

---

## Appendix D: Review & Approval

| Role                  | Name | Date | Signature |
|-----------------------|------|------|-----------|
| Technical Lead        |      |      |           |
| Database Architect    |      |      |           |
| Security Officer      |      |      |           |
| Product Owner         |      |      |           |

---

*End of Document*
