# Implementation Guide

## Overview

This guide provides step-by-step instructions for implementing the Marketplace Category structure with PostgreSQL (JSONB) and Elasticsearch.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Database Setup](#database-setup)
4. [Elasticsearch Setup](#elasticsearch-setup)
5. [Application Implementation](#application-implementation)
6. [Testing](#testing)
7. [Deployment](#deployment)
8. [Monitoring & Maintenance](#monitoring--maintenance)

---

## 1. Prerequisites

### Required Software

- **PostgreSQL**: Version 12 or higher (for JSONB support)
- **Elasticsearch**: Version 7.x or 8.x
- **Node.js**: Version 16+ (if using Node.js backend)
- **Docker**: For containerized deployment
- **Git**: For version control

### Required Skills

- SQL and PostgreSQL JSONB operations
- Elasticsearch query DSL
- RESTful API development
- Basic DevOps knowledge

---

## 2. Environment Setup

### 2.1 Local Development Environment

```bash
# Clone the repository
git clone https://github.com/your-org/cios-content-service.git
cd cios-content-service

# Install dependencies (example for Node.js)
npm install

# Copy environment configuration
cp .env.example .env

# Edit .env with your local settings
vim .env
```

### 2.2 Environment Variables

```bash
# .env file
# Database Configuration
DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=marketplace_db
DATABASE_USER=postgres
DATABASE_PASSWORD=your_secure_password
DATABASE_SSL=false

# Elasticsearch Configuration
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=your_elastic_password
ELASTICSEARCH_INDEX_PREFIX=marketplace

# Application Configuration
NODE_ENV=development
PORT=3000
API_VERSION=v1

# JWT Configuration
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRY=3600

# Partner API Configuration
PARTNER_API_KEY_SALT=your_api_key_salt

# Logging
LOG_LEVEL=debug
```

### 2.3 Docker Setup

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_DB: marketplace_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./db/init:/docker-entrypoint-initdb.d

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.6.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - elasticsearch_data:/usr/share/elasticsearch/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  elasticsearch_data:
  redis_data:
```

```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f
```

---

## 3. Database Setup

### 3.1 Create Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE marketplace_db;

# Connect to the new database
\c marketplace_db
```

### 3.2 Enable Extensions

```sql
-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable trigram extension for fuzzy search
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Verify extensions
\dx
```

### 3.3 Create Tables

```sql
-- Run the schema from MARKETPLACE_CATEGORY_DESIGN.md
-- Section 4.1: Categories Table
-- (Copy the full CREATE TABLE statement)

-- Run the supporting tables
-- Section 4.3: Supporting Tables
-- (Copy all supporting table CREATE statements)
```

### 3.4 Database Migration with Flyway

```bash
# Install Flyway
wget -qO- https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/9.15.1/flyway-commandline-9.15.1-linux-x64.tar.gz | tar xvz && sudo ln -s `pwd`/flyway-9.15.1/flyway /usr/local/bin

# Create migration directory
mkdir -p db/migrations

# Create first migration file
# db/migrations/V1__create_marketplace_categories.sql
```

Example migration file:

```sql
-- V1__create_marketplace_categories.sql
-- Create marketplace_categories table
CREATE TABLE marketplace_categories (
    -- Full table definition here
);

-- Create indexes
CREATE INDEX idx_categories_slug ON marketplace_categories(slug);
-- ... other indexes

-- Create triggers
CREATE OR REPLACE FUNCTION update_categories_search_vector()
RETURNS TRIGGER AS $$
-- Trigger definition here
$$;
```

Run migrations:

```bash
flyway -url=jdbc:postgresql://localhost:5432/marketplace_db \
       -user=postgres \
       -password=postgres \
       migrate
```

### 3.5 Seed Initial Data

```sql
-- Insert sample category
INSERT INTO marketplace_categories (
    name, slug, description, icon_url, display_order, status, sub_categories
) VALUES (
    'Technology & Innovation',
    'technology-innovation',
    'Explore cutting-edge technology and innovative solutions',
    'https://cdn.example.com/icons/technology.svg',
    1,
    'active',
    '[
        {
            "id": "770e8400-e29b-41d4-a716-446655440002",
            "name": "Web Development",
            "slug": "web-development",
            "description": "Learn modern web development",
            "display_order": 1,
            "articles": []
        }
    ]'::jsonb
);
```

### 3.6 Verify Database Setup

```sql
-- Check tables
\dt

-- Check indexes
\di

-- Verify data
SELECT id, name, slug, jsonb_array_length(sub_categories) as subcategory_count
FROM marketplace_categories;
```

---

## 4. Elasticsearch Setup

### 4.1 Create Index

```bash
# Create the marketplace_categories index
curl -X PUT "localhost:9200/marketplace_categories" \
  -H 'Content-Type: application/json' \
  -d @elasticsearch/mappings/categories.json
```

### 4.2 Index Mapping File

Create file `elasticsearch/mappings/categories.json`:

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
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "name": {
        "type": "text",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "sub_categories": {
        "type": "nested",
        "properties": {
          "id": {"type": "keyword"},
          "name": {"type": "text"},
          "articles": {
            "type": "nested",
            "properties": {
              "id": {"type": "keyword"},
              "title": {"type": "text"},
              "tags": {"type": "keyword"}
            }
          }
        }
      }
    }
  }
}
```

### 4.3 Verify Index Creation

```bash
# Check index exists
curl -X GET "localhost:9200/_cat/indices?v"

# Get index mapping
curl -X GET "localhost:9200/marketplace_categories/_mapping?pretty"

# Get index settings
curl -X GET "localhost:9200/marketplace_categories/_settings?pretty"
```

### 4.4 Index Initial Data

```bash
# Index a document
curl -X POST "localhost:9200/marketplace_categories/_doc" \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Technology & Innovation",
    "slug": "technology-innovation",
    "sub_categories": []
  }'
```

---

## 5. Application Implementation

### 5.1 Project Structure

```
cios-content-service/
├── src/
│   ├── config/          # Configuration files
│   ├── models/          # Data models
│   ├── controllers/     # API controllers
│   ├── services/        # Business logic
│   ├── repositories/    # Data access layer
│   ├── middleware/      # Express middleware
│   ├── routes/          # API routes
│   ├── utils/           # Utility functions
│   └── index.js         # Application entry point
├── db/
│   └── migrations/      # Database migrations
├── elasticsearch/
│   └── mappings/        # ES index mappings
├── tests/               # Test files
├── docs/                # Documentation
├── .env.example
├── docker-compose.yml
├── package.json
└── README.md
```

### 5.2 Database Repository Layer

```javascript
// src/repositories/categoryRepository.js
const { Pool } = require('pg');

class CategoryRepository {
  constructor(pool) {
    this.pool = pool;
  }

  async createCategory(categoryData) {
    const query = `
      INSERT INTO marketplace_categories (
        name, slug, description, icon_url, display_order, 
        status, sub_categories, metadata
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
      RETURNING *
    `;
    
    const values = [
      categoryData.name,
      categoryData.slug,
      categoryData.description,
      categoryData.icon_url,
      categoryData.display_order,
      categoryData.status || 'active',
      JSON.stringify(categoryData.sub_categories || []),
      JSON.stringify(categoryData.metadata || {})
    ];

    const result = await this.pool.query(query, values);
    return result.rows[0];
  }

  async getCategoryById(id) {
    const query = `
      SELECT * FROM marketplace_categories 
      WHERE id = $1 AND deleted_at IS NULL
    `;
    const result = await this.pool.query(query, [id]);
    return result.rows[0];
  }

  async getAllCategories(filters = {}) {
    let query = `
      SELECT 
        id, name, slug, description, icon_url, 
        display_order, status,
        jsonb_array_length(sub_categories) as subcategory_count,
        created_at, updated_at
      FROM marketplace_categories 
      WHERE deleted_at IS NULL
    `;

    if (filters.status) {
      query += ` AND status = '${filters.status}'`;
    }

    query += ` ORDER BY display_order ASC`;

    const result = await this.pool.query(query);
    return result.rows;
  }

  async addSubCategory(categoryId, subCategoryData) {
    const query = `
      UPDATE marketplace_categories
      SET sub_categories = sub_categories || $1::jsonb,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = $2
      RETURNING *
    `;

    const subCategory = {
      id: uuidv4(),
      ...subCategoryData,
      articles: []
    };

    const result = await this.pool.query(
      query, 
      [JSON.stringify([subCategory]), categoryId]
    );
    
    return result.rows[0];
  }

  async searchArticlesByTag(tag) {
    const query = `
      SELECT 
        c.id as category_id,
        c.name as category_name,
        sc.value->>'id' as subcategory_id,
        sc.value->>'name' as subcategory_name,
        a.value->>'id' as article_id,
        a.value->>'title' as article_title,
        a.value->'tags' as tags
      FROM marketplace_categories c,
          jsonb_array_elements(c.sub_categories) sc,
          jsonb_array_elements(sc.value->'articles') a
      WHERE a.value->'tags' ? $1
          AND c.status = 'active'
          AND a.value->>'status' = 'published'
    `;

    const result = await this.pool.query(query, [tag]);
    return result.rows;
  }
}

module.exports = CategoryRepository;
```

### 5.3 Elasticsearch Service Layer

```javascript
// src/services/elasticsearchService.js
const { Client } = require('@elastic/elasticsearch');

class ElasticsearchService {
  constructor(config) {
    this.client = new Client({
      node: `http://${config.host}:${config.port}`,
      auth: {
        username: config.username,
        password: config.password
      }
    });
    this.indexName = `${config.indexPrefix}_categories`;
  }

  async indexCategory(category) {
    try {
      await this.client.index({
        index: this.indexName,
        id: category.id,
        body: category,
        refresh: true
      });
      return { success: true };
    } catch (error) {
      console.error('Elasticsearch indexing error:', error);
      throw error;
    }
  }

  async searchCategories(query, filters = {}) {
    const body = {
      query: {
        bool: {
          must: [
            {
              multi_match: {
                query: query,
                fields: ['name^3', 'description', 'sub_categories.name^2']
              }
            }
          ],
          filter: []
        }
      }
    };

    if (filters.status) {
      body.query.bool.filter.push({
        term: { status: filters.status }
      });
    }

    const result = await this.client.search({
      index: this.indexName,
      body: body
    });

    return result.hits.hits.map(hit => ({
      ...hit._source,
      score: hit._score
    }));
  }

  async searchArticles(query, filters = {}) {
    const body = {
      query: {
        nested: {
          path: 'sub_categories.articles',
          query: {
            bool: {
              must: [
                {
                  match: {
                    'sub_categories.articles.title': query
                  }
                }
              ]
            }
          },
          inner_hits: {
            name: 'matching_articles',
            size: 10
          }
        }
      }
    };

    if (filters.difficulty) {
      body.query.nested.query.bool.must.push({
        term: {
          'sub_categories.articles.difficulty_level': filters.difficulty
        }
      });
    }

    const result = await this.client.search({
      index: this.indexName,
      body: body
    });

    return this.extractArticlesFromNestedResults(result);
  }

  extractArticlesFromNestedResults(result) {
    const articles = [];
    
    result.hits.hits.forEach(hit => {
      if (hit.inner_hits && hit.inner_hits.matching_articles) {
        hit.inner_hits.matching_articles.hits.hits.forEach(article => {
          articles.push({
            ...article._source,
            category: {
              id: hit._source.id,
              name: hit._source.name
            }
          });
        });
      }
    });

    return articles;
  }
}

module.exports = ElasticsearchService;
```

### 5.4 API Controller

```javascript
// src/controllers/categoryController.js
class CategoryController {
  constructor(categoryService, elasticsearchService) {
    this.categoryService = categoryService;
    this.elasticsearchService = elasticsearchService;
  }

  async createCategory(req, res) {
    try {
      const category = await this.categoryService.createCategory(req.body);
      
      // Sync to Elasticsearch
      await this.elasticsearchService.indexCategory(category);

      res.status(201).json({
        success: true,
        data: category,
        message: 'Category created successfully'
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error.message
        }
      });
    }
  }

  async searchArticles(req, res) {
    try {
      const { q, difficulty, tags } = req.query;
      
      const results = await this.elasticsearchService.searchArticles(q, {
        difficulty,
        tags: tags?.split(',')
      });

      res.json({
        success: true,
        data: results,
        total: results.length
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        error: {
          code: 'SEARCH_ERROR',
          message: error.message
        }
      });
    }
  }
}

module.exports = CategoryController;
```

### 5.5 API Routes

```javascript
// src/routes/categoryRoutes.js
const express = require('express');
const router = express.Router();

module.exports = (categoryController) => {
  // Category routes
  router.post('/categories', categoryController.createCategory.bind(categoryController));
  router.get('/categories', categoryController.getAllCategories.bind(categoryController));
  router.get('/categories/:id', categoryController.getCategoryById.bind(categoryController));
  router.put('/categories/:id', categoryController.updateCategory.bind(categoryController));
  router.delete('/categories/:id', categoryController.deleteCategory.bind(categoryController));

  // SubCategory routes
  router.post('/categories/:id/subcategories', categoryController.addSubCategory.bind(categoryController));
  
  // Article routes
  router.post('/categories/:catId/subcategories/:subId/articles', categoryController.addArticle.bind(categoryController));
  
  // Search routes
  router.get('/search', categoryController.search.bind(categoryController));
  router.get('/search/articles', categoryController.searchArticles.bind(categoryController));

  return router;
};
```

---

## 6. Testing

### 6.1 Unit Tests

```javascript
// tests/unit/categoryService.test.js
const { expect } = require('chai');
const CategoryService = require('../../src/services/categoryService');

describe('CategoryService', () => {
  let categoryService;

  beforeEach(() => {
    // Setup mock repository
    const mockRepo = {
      createCategory: async (data) => ({ id: '123', ...data })
    };
    categoryService = new CategoryService(mockRepo);
  });

  it('should create a category', async () => {
    const categoryData = {
      name: 'Test Category',
      slug: 'test-category'
    };

    const result = await categoryService.createCategory(categoryData);
    
    expect(result).to.have.property('id');
    expect(result.name).to.equal('Test Category');
  });
});
```

### 6.2 Integration Tests

```javascript
// tests/integration/category.test.js
const request = require('supertest');
const app = require('../../src/app');

describe('Category API', () => {
  it('POST /api/v1/marketplace/categories - should create category', async () => {
    const response = await request(app)
      .post('/api/v1/marketplace/categories')
      .send({
        name: 'Technology',
        slug: 'technology',
        description: 'Tech category'
      })
      .expect(201);

    expect(response.body.success).to.be.true;
    expect(response.body.data).to.have.property('id');
  });
});
```

### 6.3 Run Tests

```bash
# Run all tests
npm test

# Run with coverage
npm run test:coverage

# Run specific test file
npm test -- tests/unit/categoryService.test.js
```

---

## 7. Deployment

### 7.1 Production Environment Variables

```bash
# Production .env
NODE_ENV=production
PORT=3000

DATABASE_HOST=prod-db.example.com
DATABASE_SSL=true
DATABASE_SSL_REJECT_UNAUTHORIZED=true

ELASTICSEARCH_HOST=prod-es.example.com
ELASTICSEARCH_SSL=true

# Use secrets management
DATABASE_PASSWORD=${SECRET:DB_PASSWORD}
JWT_SECRET=${SECRET:JWT_SECRET}
```

### 7.2 Docker Production Build

```dockerfile
# Dockerfile
FROM node:16-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

COPY . .

FROM node:16-alpine
WORKDIR /app
COPY --from=builder /app .

EXPOSE 3000
CMD ["node", "src/index.js"]
```

```bash
# Build image
docker build -t cios-content-service:latest .

# Run container
docker run -d \
  --name content-service \
  -p 3000:3000 \
  --env-file .env.production \
  cios-content-service:latest
```

### 7.3 Kubernetes Deployment

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: content-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: content-service
  template:
    metadata:
      labels:
        app: content-service
    spec:
      containers:
      - name: content-service
        image: cios-content-service:latest
        ports:
        - containerPort: 3000
        env:
        - name: DATABASE_HOST
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: host
```

---

## 8. Monitoring & Maintenance

### 8.1 Health Checks

```javascript
// src/routes/healthRoutes.js
router.get('/health', async (req, res) => {
  const health = {
    status: 'UP',
    timestamp: new Date().toISOString(),
    checks: {
      database: await checkDatabase(),
      elasticsearch: await checkElasticsearch()
    }
  };

  const statusCode = health.checks.database && health.checks.elasticsearch ? 200 : 503;
  res.status(statusCode).json(health);
});
```

### 8.2 Logging

```javascript
// Use Winston for logging
const winston = require('winston');

const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' })
  ]
});
```

### 8.3 Database Maintenance

```sql
-- Vacuum and analyze
VACUUM ANALYZE marketplace_categories;

-- Reindex
REINDEX TABLE marketplace_categories;

-- Check index usage
SELECT * FROM pg_stat_user_indexes 
WHERE schemaname = 'public';
```

### 8.4 Elasticsearch Maintenance

```bash
# Force merge segments
curl -X POST "localhost:9200/marketplace_categories/_forcemerge?max_num_segments=1"

# Clear cache
curl -X POST "localhost:9200/marketplace_categories/_cache/clear"
```

---

*End of Implementation Guide*
