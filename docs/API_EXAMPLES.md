# API Examples and Use Cases

## Overview

This document provides comprehensive API examples for the Marketplace Category service, including request/response samples and common use cases.

## Table of Contents

1. [Authentication](#authentication)
2. [Category Management](#category-management)
3. [SubCategory Management](#subcategory-management)
4. [Article Management](#article-management)
5. [Search Operations](#search-operations)
6. [Progress Tracking](#progress-tracking)
7. [Partner Integration](#partner-integration)
8. [Error Handling](#error-handling)

---

## 1. Authentication

### JWT Bearer Token Authentication

All API requests require authentication using JWT Bearer tokens.

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Get Access Token

```http
POST /api/v1/auth/token
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "securePassword123"
}

Response: 200 OK
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "refresh_token_here"
  }
}
```

---

## 2. Category Management

### 2.1 Create a New Category

```http
POST /api/v1/marketplace/categories
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Technology & Innovation",
  "slug": "technology-innovation",
  "description": "Explore cutting-edge technology and innovative solutions",
  "icon_url": "https://cdn.example.com/icons/technology.svg",
  "display_order": 1,
  "status": "active",
  "metadata": {
    "featured": true,
    "color_theme": "#0066CC",
    "banner_image": "https://cdn.example.com/banners/tech.jpg"
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Technology & Innovation",
    "slug": "technology-innovation",
    "description": "Explore cutting-edge technology and innovative solutions",
    "icon_url": "https://cdn.example.com/icons/technology.svg",
    "display_order": 1,
    "status": "active",
    "sub_categories": [],
    "metadata": {
      "featured": true,
      "color_theme": "#0066CC"
    },
    "created_at": "2024-02-10T10:30:00Z",
    "updated_at": "2024-02-10T10:30:00Z"
  },
  "message": "Category created successfully"
}
```

### 2.2 Get All Categories

```http
GET /api/v1/marketplace/categories?page=1&limit=10&status=active&sort=display_order
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Technology & Innovation",
      "slug": "technology-innovation",
      "description": "Explore cutting-edge technology and innovative solutions",
      "icon_url": "https://cdn.example.com/icons/technology.svg",
      "display_order": 1,
      "status": "active",
      "sub_categories_count": 5,
      "total_articles": 47,
      "created_at": "2024-02-10T10:30:00Z"
    },
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "name": "Business & Management",
      "slug": "business-management",
      "description": "Master business skills and management practices",
      "icon_url": "https://cdn.example.com/icons/business.svg",
      "display_order": 2,
      "status": "active",
      "sub_categories_count": 8,
      "total_articles": 62,
      "created_at": "2024-02-10T11:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 25,
    "total_pages": 3,
    "has_next": true,
    "has_prev": false
  }
}
```

### 2.3 Get Category by ID with Full Hierarchy

```http
GET /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000?include=subcategories,articles
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Technology & Innovation",
    "slug": "technology-innovation",
    "description": "Explore cutting-edge technology and innovative solutions",
    "icon_url": "https://cdn.example.com/icons/technology.svg",
    "display_order": 1,
    "status": "active",
    "sub_categories": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "name": "Web Development",
        "slug": "web-development",
        "description": "Learn modern web development technologies",
        "display_order": 1,
        "articles_count": 15,
        "articles": [
          {
            "id": "880e8400-e29b-41d4-a716-446655440003",
            "title": "Introduction to HTML5",
            "slug": "introduction-to-html5",
            "summary": "Learn the fundamentals of HTML5",
            "difficulty_level": "beginner",
            "reading_time_minutes": 20,
            "status": "published",
            "published_at": "2024-02-01T10:00:00Z"
          }
        ]
      }
    ],
    "metadata": {
      "featured": true,
      "color_theme": "#0066CC"
    },
    "created_at": "2024-02-10T10:30:00Z",
    "updated_at": "2024-02-10T10:30:00Z"
  }
}
```

### 2.4 Update Category

```http
PUT /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
Authorization: Bearer {token}

{
  "description": "Updated: Explore cutting-edge technology, AI, and innovative solutions",
  "display_order": 2,
  "metadata": {
    "featured": true,
    "color_theme": "#0066CC",
    "new_field": "additional_data"
  }
}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Technology & Innovation",
    "description": "Updated: Explore cutting-edge technology, AI, and innovative solutions",
    "display_order": 2,
    "updated_at": "2024-02-10T15:30:00Z"
  },
  "message": "Category updated successfully"
}
```

### 2.5 Delete Category (Soft Delete)

```http
DELETE /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer {token}

Response: 204 No Content
```

---

## 3. SubCategory Management

### 3.1 Add SubCategory to Category

```http
POST /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000/subcategories
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "Web Development",
  "slug": "web-development",
  "description": "Master modern web development with HTML, CSS, JavaScript, and frameworks",
  "icon_url": "https://cdn.example.com/icons/web-dev.svg",
  "display_order": 1,
  "metadata": {
    "difficulty_level": "beginner",
    "estimated_duration": "3 months",
    "prerequisites": [],
    "learning_outcomes": [
      "Build responsive websites",
      "Understand web standards",
      "Deploy web applications"
    ]
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "category_id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Web Development",
    "slug": "web-development",
    "description": "Master modern web development with HTML, CSS, JavaScript, and frameworks",
    "icon_url": "https://cdn.example.com/icons/web-dev.svg",
    "display_order": 1,
    "articles": [],
    "metadata": {
      "difficulty_level": "beginner",
      "estimated_duration": "3 months",
      "prerequisites": []
    },
    "created_at": "2024-02-10T11:00:00Z"
  },
  "message": "SubCategory created successfully"
}
```

### 3.2 List All SubCategories in a Category

```http
GET /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000/subcategories?sort=display_order
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "name": "Web Development",
      "slug": "web-development",
      "description": "Master modern web development",
      "display_order": 1,
      "articles_count": 15
    },
    {
      "id": "771e8400-e29b-41d4-a716-446655440003",
      "name": "Mobile Development",
      "slug": "mobile-development",
      "description": "Build native and cross-platform mobile apps",
      "display_order": 2,
      "articles_count": 12
    }
  ],
  "total": 2
}
```

### 3.3 Update SubCategory

```http
PUT /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000/subcategories/770e8400-e29b-41d4-a716-446655440002
Content-Type: application/json
Authorization: Bearer {token}

{
  "description": "Updated: Master modern web development with latest technologies",
  "metadata": {
    "difficulty_level": "intermediate"
  }
}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "description": "Updated: Master modern web development with latest technologies",
    "updated_at": "2024-02-10T12:00:00Z"
  },
  "message": "SubCategory updated successfully"
}
```

---

## 4. Article Management

### 4.1 Add Article to SubCategory

```http
POST /api/v1/marketplace/categories/550e8400-e29b-41d4-a716-446655440000/subcategories/770e8400-e29b-41d4-a716-446655440002/articles
Content-Type: application/json
Authorization: Bearer {token}

{
  "title": "Complete Guide to HTML5 and Semantic Markup",
  "slug": "complete-guide-html5-semantic-markup",
  "content_type": "tutorial",
  "summary": "Learn HTML5 from basics to advanced concepts including semantic elements, forms, multimedia, and best practices",
  "content_url": "https://content.example.com/articles/html5-guide",
  "thumbnail_url": "https://cdn.example.com/thumbs/html5.jpg",
  "author": {
    "id": "990e8400-e29b-41d4-a716-446655440004",
    "name": "Jane Developer",
    "email": "jane@example.com",
    "avatar_url": "https://cdn.example.com/avatars/jane.jpg"
  },
  "tags": ["html5", "web-development", "beginner", "tutorial"],
  "status": "published",
  "visibility": "public",
  "language": "en",
  "reading_time_minutes": 25,
  "difficulty_level": "beginner",
  "external_course_info": {
    "is_external": false,
    "provider": null,
    "course_id": null,
    "enrollment_url": null,
    "progress_tracking_enabled": true
  },
  "seo_metadata": {
    "meta_title": "Complete HTML5 Guide - Learn Semantic Markup",
    "meta_description": "Comprehensive HTML5 tutorial covering semantic elements, forms, multimedia, and best practices for modern web development",
    "keywords": ["html5 tutorial", "semantic markup", "web development", "html5 guide"]
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "880e8400-e29b-41d4-a716-446655440005",
    "category_id": "550e8400-e29b-41d4-a716-446655440000",
    "subcategory_id": "770e8400-e29b-41d4-a716-446655440002",
    "title": "Complete Guide to HTML5 and Semantic Markup",
    "slug": "complete-guide-html5-semantic-markup",
    "content_type": "tutorial",
    "summary": "Learn HTML5 from basics to advanced concepts...",
    "status": "published",
    "published_at": "2024-02-10T12:30:00Z",
    "created_at": "2024-02-10T12:30:00Z"
  },
  "message": "Article created successfully"
}
```

### 4.2 Get Article Details

```http
GET /api/v1/marketplace/articles/880e8400-e29b-41d4-a716-446655440005
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "880e8400-e29b-41d4-a716-446655440005",
    "title": "Complete Guide to HTML5 and Semantic Markup",
    "slug": "complete-guide-html5-semantic-markup",
    "content_type": "tutorial",
    "summary": "Learn HTML5 from basics to advanced concepts...",
    "content_url": "https://content.example.com/articles/html5-guide",
    "thumbnail_url": "https://cdn.example.com/thumbs/html5.jpg",
    "author": {
      "id": "990e8400-e29b-41d4-a716-446655440004",
      "name": "Jane Developer",
      "email": "jane@example.com",
      "avatar_url": "https://cdn.example.com/avatars/jane.jpg"
    },
    "tags": ["html5", "web-development", "beginner", "tutorial"],
    "status": "published",
    "visibility": "public",
    "language": "en",
    "reading_time_minutes": 25,
    "difficulty_level": "beginner",
    "engagement_metrics": {
      "views": 1250,
      "likes": 98,
      "bookmarks": 45,
      "completions": 780,
      "average_rating": 4.5,
      "total_ratings": 123
    },
    "category": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Technology & Innovation"
    },
    "subcategory": {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "name": "Web Development"
    },
    "published_at": "2024-02-10T12:30:00Z",
    "created_at": "2024-02-10T12:30:00Z",
    "updated_at": "2024-02-10T12:30:00Z"
  }
}
```

### 4.3 Update Article

```http
PATCH /api/v1/marketplace/articles/880e8400-e29b-41d4-a716-446655440005
Content-Type: application/json
Authorization: Bearer {token}

{
  "summary": "Updated: Comprehensive HTML5 tutorial with new examples",
  "tags": ["html5", "web-development", "beginner", "tutorial", "2024"],
  "reading_time_minutes": 30
}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "880e8400-e29b-41d4-a716-446655440005",
    "updated_at": "2024-02-10T14:00:00Z"
  },
  "message": "Article updated successfully"
}
```

### 4.4 Bulk Article Operations

```http
POST /api/v1/marketplace/articles/bulk-update
Content-Type: application/json
Authorization: Bearer {token}

{
  "article_ids": [
    "880e8400-e29b-41d4-a716-446655440005",
    "880e8400-e29b-41d4-a716-446655440006"
  ],
  "updates": {
    "status": "published",
    "visibility": "public"
  }
}

Response: 200 OK
{
  "success": true,
  "data": {
    "updated_count": 2,
    "failed_count": 0
  },
  "message": "Bulk update completed successfully"
}
```

---

## 5. Search Operations

### 5.1 Global Search (All Types)

```http
GET /api/v1/marketplace/search?q=web+development&type=all&page=1&limit=20
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "categories": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Technology & Innovation",
        "type": "category",
        "relevance_score": 8.5
      }
    ],
    "subcategories": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "name": "Web Development",
        "category_name": "Technology & Innovation",
        "type": "subcategory",
        "relevance_score": 9.2
      }
    ],
    "articles": [
      {
        "id": "880e8400-e29b-41d4-a716-446655440005",
        "title": "Complete Guide to HTML5 and Semantic Markup",
        "summary": "Learn HTML5 from basics...",
        "type": "article",
        "relevance_score": 8.8,
        "highlight": "Learn <em>web development</em> with HTML5..."
      }
    ]
  },
  "total_results": 45,
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 45,
    "total_pages": 3
  }
}
```

### 5.2 Advanced Article Search with Filters

```http
GET /api/v1/marketplace/search/articles?q=html&difficulty=beginner&tags=tutorial,web-development&language=en&status=published&sort=rating&order=desc
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440005",
      "title": "Complete Guide to HTML5 and Semantic Markup",
      "summary": "Learn HTML5 from basics...",
      "difficulty_level": "beginner",
      "tags": ["html5", "tutorial", "web-development"],
      "average_rating": 4.5,
      "views": 1250,
      "category": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Technology & Innovation"
      },
      "subcategory": {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "name": "Web Development"
      }
    }
  ],
  "filters_applied": {
    "difficulty": "beginner",
    "tags": ["tutorial", "web-development"],
    "language": "en",
    "status": "published"
  },
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 15
  }
}
```

### 5.3 Autocomplete Search

```http
GET /api/v1/marketplace/search/autocomplete?q=web+dev&limit=10
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "suggestions": [
    {
      "text": "Web Development",
      "type": "subcategory",
      "id": "770e8400-e29b-41d4-a716-446655440002"
    },
    {
      "text": "Web Development Fundamentals",
      "type": "article",
      "id": "880e8400-e29b-41d4-a716-446655440010"
    },
    {
      "text": "Web Design",
      "type": "subcategory",
      "id": "770e8400-e29b-41d4-a716-446655440015"
    }
  ]
}
```

### 5.4 Faceted Search

```http
POST /api/v1/marketplace/search/faceted
Content-Type: application/json
Authorization: Bearer {token}

{
  "query": "programming",
  "facets": ["difficulty_level", "tags", "language", "content_type"],
  "filters": {
    "status": "published"
  }
}

Response: 200 OK
{
  "success": true,
  "data": [...],
  "facets": {
    "difficulty_level": {
      "beginner": 45,
      "intermediate": 32,
      "advanced": 18
    },
    "tags": {
      "javascript": 28,
      "python": 25,
      "java": 20
    },
    "language": {
      "en": 85,
      "es": 10
    },
    "content_type": {
      "tutorial": 50,
      "video": 30,
      "documentation": 15
    }
  }
}
```

---

## 6. Progress Tracking

### 6.1 Update Course Progress

```http
POST /api/v1/marketplace/progress
Content-Type: application/json
Authorization: Bearer {token}

{
  "article_id": "880e8400-e29b-41d4-a716-446655440005",
  "status": "in_progress",
  "progress_percentage": 65,
  "metadata": {
    "current_section": "Section 3: Forms",
    "time_spent_minutes": 45
  }
}

Response: 200 OK
{
  "success": true,
  "data": {
    "id": "aa0e8400-e29b-41d4-a716-446655440020",
    "user_id": "user_uuid",
    "article_id": "880e8400-e29b-41d4-a716-446655440005",
    "status": "in_progress",
    "progress_percentage": 65,
    "last_accessed_at": "2024-02-10T15:30:00Z",
    "updated_at": "2024-02-10T15:30:00Z"
  },
  "message": "Progress updated successfully"
}
```

### 6.2 Get User Progress Summary

```http
GET /api/v1/marketplace/progress?user_id=current&status=in_progress&page=1&limit=20
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": [
    {
      "article": {
        "id": "880e8400-e29b-41d4-a716-446655440005",
        "title": "Complete Guide to HTML5",
        "thumbnail_url": "https://cdn.example.com/thumbs/html5.jpg"
      },
      "progress": {
        "status": "in_progress",
        "progress_percentage": 65,
        "started_at": "2024-02-08T10:00:00Z",
        "last_accessed_at": "2024-02-10T15:30:00Z"
      }
    }
  ],
  "summary": {
    "total_enrolled": 25,
    "in_progress": 8,
    "completed": 15,
    "not_started": 2
  },
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 8
  }
}
```

### 6.3 Mark Article as Complete

```http
POST /api/v1/marketplace/progress/complete
Content-Type: application/json
Authorization: Bearer {token}

{
  "article_id": "880e8400-e29b-41d4-a716-446655440005",
  "completion_data": {
    "quiz_score": 85,
    "time_spent_minutes": 120
  }
}

Response: 200 OK
{
  "success": true,
  "data": {
    "status": "completed",
    "progress_percentage": 100,
    "completion_date": "2024-02-10T16:00:00Z",
    "certificate_url": "https://certificates.example.com/cert_12345.pdf"
  },
  "message": "Article marked as complete"
}
```

---

## 7. Partner Integration

### 7.1 Register as Partner

```http
POST /api/v1/partners/register
Content-Type: application/json

{
  "name": "TechEdu Platform",
  "slug": "techedu-platform",
  "description": "Leading online learning platform for technology courses",
  "website_url": "https://techedu.example.com",
  "logo_url": "https://techedu.example.com/logo.png",
  "contact_info": {
    "email": "partnerships@techedu.example.com",
    "phone": "+1-234-567-8900",
    "contact_person": "John Smith"
  },
  "sso_config": {
    "provider": "oauth2",
    "auth_endpoint": "https://techedu.example.com/oauth/authorize",
    "token_endpoint": "https://techedu.example.com/oauth/token"
  }
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "partner_uuid",
    "name": "TechEdu Platform",
    "status": "pending",
    "created_at": "2024-02-10T10:00:00Z"
  },
  "message": "Partner registration submitted for review"
}
```

### 7.2 Partner SSO Authentication

```http
POST /api/v1/partners/sso/authenticate
Content-Type: application/json

{
  "partner_id": "partner_uuid",
  "authorization_code": "auth_code_from_oauth",
  "redirect_uri": "https://marketplace.example.com/auth/callback"
}

Response: 200 OK
{
  "success": true,
  "data": {
    "access_token": "marketplace_access_token",
    "user_info": {
      "id": "user_uuid",
      "email": "user@example.com",
      "name": "John Doe"
    }
  }
}
```

### 7.3 Partner Progress Webhook

```http
POST /api/v1/webhooks/course-progress
Content-Type: application/json
Authorization: Bearer {partner_api_key}
X-Webhook-Signature: sha256_signature

{
  "partner_id": "partner_uuid",
  "user_id": "user_uuid",
  "article_id": "article_uuid",
  "progress_percentage": 75,
  "status": "in_progress",
  "external_course_id": "ext_course_123",
  "metadata": {
    "last_module": "Module 5: Advanced Topics",
    "quiz_scores": [85, 90, 78]
  },
  "timestamp": "2024-02-10T15:45:00Z"
}

Response: 200 OK
{
  "success": true,
  "message": "Progress update received"
}
```

---

## 8. Error Handling

### 8.1 Common Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      "field": "additional error context"
    },
    "timestamp": "2024-02-10T10:00:00Z",
    "request_id": "req_12345"
  }
}
```

### 8.2 Error Codes

| Status Code | Error Code | Description |
|------------|------------|-------------|
| 400 | INVALID_REQUEST | Malformed request or missing required fields |
| 401 | UNAUTHORIZED | Missing or invalid authentication token |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | NOT_FOUND | Resource not found |
| 409 | CONFLICT | Resource already exists (e.g., duplicate slug) |
| 422 | VALIDATION_ERROR | Request validation failed |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests |
| 500 | INTERNAL_ERROR | Server error |
| 503 | SERVICE_UNAVAILABLE | Service temporarily unavailable |

### 8.3 Error Examples

#### Validation Error

```http
POST /api/v1/marketplace/categories
Content-Type: application/json
Authorization: Bearer {token}

{
  "name": "",
  "slug": "invalid slug with spaces"
}

Response: 422 Unprocessable Entity
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "name": "Name is required and cannot be empty",
      "slug": "Slug can only contain lowercase letters, numbers, and hyphens"
    },
    "timestamp": "2024-02-10T10:00:00Z"
  }
}
```

#### Resource Not Found

```http
GET /api/v1/marketplace/categories/invalid-uuid
Authorization: Bearer {token}

Response: 404 Not Found
{
  "success": false,
  "error": {
    "code": "CATEGORY_NOT_FOUND",
    "message": "Category with ID 'invalid-uuid' not found",
    "details": {
      "category_id": "invalid-uuid"
    },
    "timestamp": "2024-02-10T10:00:00Z"
  }
}
```

#### Rate Limit Exceeded

```http
Response: 429 Too Many Requests
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Please try again later.",
    "details": {
      "limit": 100,
      "window": "1 hour",
      "retry_after": 1800
    },
    "timestamp": "2024-02-10T10:00:00Z"
  },
  "retry_after": 1800
}
```

---

## Common Use Cases

### Use Case 1: Browse Categories and Articles

```
1. GET /api/v1/marketplace/categories
2. GET /api/v1/marketplace/categories/{id}?include=subcategories
3. GET /api/v1/marketplace/categories/{cat_id}/subcategories/{sub_id}/articles
4. GET /api/v1/marketplace/articles/{article_id}
```

### Use Case 2: Search for Learning Content

```
1. GET /api/v1/marketplace/search/autocomplete?q=web
2. GET /api/v1/marketplace/search?q=web+development&type=articles
3. GET /api/v1/marketplace/articles/{article_id}
4. POST /api/v1/marketplace/progress (start learning)
```

### Use Case 3: Track Learning Progress

```
1. POST /api/v1/marketplace/progress (initial enrollment)
2. POST /api/v1/marketplace/progress (update progress)
3. GET /api/v1/marketplace/progress?user_id=current
4. POST /api/v1/marketplace/progress/complete
```

### Use Case 4: Partner Content Integration

```
1. POST /api/v1/partners/register
2. Await approval
3. POST /api/v1/marketplace/categories/{cat}/subcategories/{sub}/articles
4. POST /api/v1/webhooks/course-progress (update user progress)
```

---

*End of API Examples Documentation*
