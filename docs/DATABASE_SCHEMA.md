# Database Schema Documentation

## Overview

This document provides detailed database schema information for the Marketplace Category structure.

## Entity Relationship Diagram (ERD)

```
┌──────────────────────────────────────────────────────────────┐
│                  marketplace_categories                       │
├──────────────────────────────────────────────────────────────┤
│ PK │ id                    UUID                              │
│    │ name                  VARCHAR(255)                      │
│    │ slug                  VARCHAR(255) UNIQUE               │
│    │ description           TEXT                              │
│    │ icon_url              VARCHAR(500)                      │
│    │ display_order         INTEGER                           │
│    │ status                VARCHAR(20)                       │
│    │ sub_categories        JSONB ◄───────┐                  │
│    │ metadata              JSONB         │                  │
│    │ partner_info          JSONB         │                  │
│    │ created_at            TIMESTAMP     │                  │
│    │ updated_at            TIMESTAMP     │                  │
│    │ created_by            UUID          │                  │
│    │ updated_by            UUID          │                  │
│    │ deleted_at            TIMESTAMP     │                  │
│    │ search_vector         TSVECTOR      │                  │
└──────────────────────────────────────────┼──────────────────┘
                                           │
                                           │ Contains (JSONB)
                    ┌──────────────────────┘
                    │
                    ▼
        ┌────────────────────────┐
        │    SubCategory JSON    │
        ├────────────────────────┤
        │ - id                   │
        │ - name                 │
        │ - slug                 │
        │ - description          │
        │ - metadata             │
        │ - articles[] ◄─────┐   │
        └────────────────────┼───┘
                             │
                             │ Contains (JSONB)
              ┌──────────────┘
              │
              ▼
    ┌──────────────────┐
    │   Article JSON   │
    ├──────────────────┤
    │ - id             │
    │ - title          │
    │ - content_url    │
    │ - author         │
    │ - tags           │
    │ - status         │
    │ - metadata       │
    └──────────────────┘


┌─────────────────────────────────────────────────────────────┐
│               external_course_progress                       │
├─────────────────────────────────────────────────────────────┤
│ PK │ id                    UUID                             │
│ FK │ user_id               UUID                             │
│ FK │ category_id           UUID ──────┐                     │
│    │ subcategory_id        UUID       │                     │
│    │ article_id            UUID       │                     │
│    │ status                VARCHAR(20)│                     │
│    │ progress_percentage   INTEGER    │                     │
│    │ external_provider     VARCHAR    │                     │
│    │ external_course_id    VARCHAR    │                     │
│    │ enrollment_date       TIMESTAMP  │                     │
│    │ completion_date       TIMESTAMP  │                     │
│    │ certificate_url       VARCHAR    │                     │
│    │ started_at            TIMESTAMP  │                     │
│    │ last_accessed_at      TIMESTAMP  │                     │
│    │ created_at            TIMESTAMP  │                     │
│    │ updated_at            TIMESTAMP  │                     │
└─────────────────────────────────────────┼───────────────────┘
                                          │
                                          │ References
                                          │
                    ┌─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│                 marketplace_partners                         │
├─────────────────────────────────────────────────────────────┤
│ PK │ id                    UUID                             │
│    │ name                  VARCHAR(255)                     │
│    │ slug                  VARCHAR(255) UNIQUE              │
│    │ description           TEXT                             │
│    │ logo_url              VARCHAR(500)                     │
│    │ website_url           VARCHAR(500)                     │
│    │ sso_config            JSONB                            │
│    │ api_config            JSONB                            │
│    │ status                VARCHAR(20)                      │
│    │ category_ids          UUID[]                           │
│    │ contact_info          JSONB                            │
│    │ created_at            TIMESTAMP                        │
│    │ updated_at            TIMESTAMP                        │
│    │ approved_at           TIMESTAMP                        │
│    │ approved_by           UUID                             │
└─────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────┐
│                  article_engagement                          │
├─────────────────────────────────────────────────────────────┤
│ PK │ id                    UUID                             │
│ FK │ category_id           UUID ──────┐                     │
│    │ subcategory_id        UUID       │                     │
│    │ article_id            UUID       │                     │
│    │ user_id               UUID       │                     │
│    │ session_id            VARCHAR    │                     │
│    │ engagement_type       VARCHAR(50)│                     │
│    │ rating                INTEGER    │                     │
│    │ metadata              JSONB      │                     │
│    │ created_at            TIMESTAMP  │                     │
│    │ ip_address            INET       │                     │
│    │ user_agent            TEXT       │                     │
└─────────────────────────────────────────┼───────────────────┘
                                          │
                                          │ References
                                          └──────────────────────┐
                                                                 │
                                                                 ▼
                                    marketplace_categories (shown above)
```

## Table Relationships

### Primary Relationships

1. **marketplace_categories**
   - Self-contained with nested JSONB structure
   - Contains sub_categories as JSONB array
   - Each subcategory contains articles as JSONB array

2. **external_course_progress**
   - Foreign key: category_id → marketplace_categories.id
   - References subcategory and article by their IDs stored in JSONB
   - Tracks user progress on articles

3. **marketplace_partners**
   - Linked to categories via category_ids array
   - Independent table for partner management
   - Contains SSO configuration in JSONB

4. **article_engagement**
   - Foreign key: category_id → marketplace_categories.id
   - References subcategory and article by their IDs
   - Tracks user interactions

## JSONB Structure Details

### Sub_categories JSONB Schema

```json
[
  {
    "id": "uuid",
    "name": "string",
    "slug": "string",
    "description": "string",
    "icon_url": "string",
    "display_order": "integer",
    "metadata": {
      "difficulty_level": "string",
      "estimated_duration": "string",
      "prerequisites": ["string"]
    },
    "articles": [
      {
        "id": "uuid",
        "title": "string",
        "slug": "string",
        "content_type": "string",
        "summary": "string",
        "content_url": "string",
        "thumbnail_url": "string",
        "author": {
          "id": "uuid",
          "name": "string",
          "email": "string",
          "avatar_url": "string"
        },
        "tags": ["string"],
        "status": "string",
        "visibility": "string",
        "language": "string",
        "reading_time_minutes": "integer",
        "difficulty_level": "string",
        "external_course_info": {
          "is_external": "boolean",
          "provider": "string",
          "course_id": "string",
          "enrollment_url": "string",
          "progress_tracking_enabled": "boolean"
        },
        "engagement_metrics": {
          "views": "integer",
          "likes": "integer",
          "bookmarks": "integer",
          "completions": "integer",
          "average_rating": "float",
          "total_ratings": "integer"
        },
        "seo_metadata": {
          "meta_title": "string",
          "meta_description": "string",
          "keywords": ["string"]
        },
        "published_at": "timestamp",
        "updated_at": "timestamp",
        "created_at": "timestamp"
      }
    ]
  }
]
```

## Index Strategy

### PostgreSQL Indexes

1. **Primary Indexes**
   - PRIMARY KEY on id columns
   - UNIQUE on slug columns

2. **Query Optimization Indexes**
   - B-tree indexes on frequently queried columns (status, display_order, created_at)
   - GIN indexes on JSONB columns for nested queries
   - GIN index on search_vector for full-text search

3. **Partial Indexes**
   - Filtered indexes on status WHERE deleted_at IS NULL

### Elasticsearch Indexes

1. **Field-level Indexing**
   - Keyword fields for exact matching
   - Text fields with custom analyzers for search
   - Autocomplete fields with edge_ngram tokenizer

2. **Nested Indexing**
   - Nested type for sub_categories
   - Nested type for articles within sub_categories
   - Supports independent querying of nested documents

## Performance Considerations

### PostgreSQL

1. **JSONB Benefits**
   - Efficient binary storage
   - Fast nested queries with GIN indexes
   - Supports indexing on specific paths
   - Allows schema flexibility

2. **Query Optimization**
   - Use JSONB operators for nested queries
   - Utilize GIN indexes for containment queries
   - Implement connection pooling
   - Use prepared statements

### Elasticsearch

1. **Search Benefits**
   - Fast full-text search
   - Efficient aggregations
   - Real-time analytics
   - Horizontal scaling

2. **Optimization**
   - Proper shard allocation
   - Replica configuration
   - Index lifecycle management
   - Query caching

## Data Integrity

### Constraints

1. **NOT NULL Constraints**
   - Essential fields cannot be null
   - Ensures data completeness

2. **CHECK Constraints**
   - Status values restricted to enum
   - Percentage values within valid range

3. **UNIQUE Constraints**
   - Slug uniqueness
   - User-article combination for progress

### Triggers

1. **search_vector Update**
   - Automatically updates on INSERT/UPDATE
   - Maintains full-text search index

2. **updated_at Timestamp**
   - Automatically updates on row modification
   - Tracks last modification time

## Migration Scripts

### Initial Schema Creation

```sql
-- Run in order:
1. Create extensions (if needed)
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
   CREATE EXTENSION IF NOT EXISTS "pg_trgm";

2. Create tables
   - marketplace_categories
   - external_course_progress
   - marketplace_partners
   - article_engagement

3. Create indexes

4. Create triggers

5. Insert initial data (if any)
```

### Schema Versioning

Use migration tools like:
- Flyway
- Liquibase
- Alembic (Python)
- TypeORM migrations (Node.js)

## Backup and Recovery

### PostgreSQL

1. **Backup Strategy**
   - Daily full backups
   - Continuous archiving with WAL
   - Point-in-time recovery capability

2. **JSONB Considerations**
   - JSONB data included in regular backups
   - Test restore procedures regularly

### Elasticsearch

1. **Snapshot Strategy**
   - Daily snapshots to object storage
   - Incremental snapshots
   - Cross-cluster replication for DR

## Scaling Considerations

### Vertical Scaling

- Increase server resources (CPU, RAM)
- Optimize queries and indexes
- Tune PostgreSQL configuration

### Horizontal Scaling

- Read replicas for PostgreSQL
- Elasticsearch cluster expansion
- Load balancing across replicas

## Security

### Data Protection

1. **Encryption**
   - At rest: PostgreSQL TDE
   - In transit: TLS/SSL

2. **Access Control**
   - Row-level security (RLS)
   - Role-based permissions
   - Limited direct database access

3. **Sensitive Data**
   - Password hashing
   - API key encryption
   - PII data handling

### Audit

- Track all schema changes
- Log data modifications
- Monitor access patterns

---

*This schema documentation is version-controlled and should be updated with any schema changes.*
