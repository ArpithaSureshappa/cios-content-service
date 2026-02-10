# Quick Reference Guide

## Overview

This quick reference guide provides essential commands, queries, and code snippets for working with the Marketplace Category system.

## Table of Contents

1. [Common SQL Queries](#common-sql-queries)
2. [Elasticsearch Queries](#elasticsearch-queries)
3. [API Quick Reference](#api-quick-reference)
4. [JSONB Operations](#jsonb-operations)
5. [Useful Commands](#useful-commands)

---

## Common SQL Queries

### Basic Category Operations

```sql
-- Get all active categories with subcategory count
SELECT 
    id, name, slug,
    jsonb_array_length(sub_categories) as subcategory_count,
    status
FROM marketplace_categories
WHERE status = 'active' AND deleted_at IS NULL
ORDER BY display_order;

-- Get category with full hierarchy
SELECT * FROM marketplace_categories 
WHERE id = 'your-category-id';

-- Count total articles per category
SELECT 
    c.id,
    c.name,
    COUNT(*) as total_articles
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
GROUP BY c.id, c.name;
```

### Article Queries

```sql
-- Find articles by tag
SELECT 
    c.name as category,
    sc.value->>'name' as subcategory,
    a.value->>'title' as article
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE a.value->'tags' ? 'html'
    AND a.value->>'status' = 'published';

-- Find articles by difficulty level
SELECT 
    a.value->>'title' as title,
    a.value->>'difficulty_level' as difficulty
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE a.value->>'difficulty_level' = 'beginner';

-- Get most viewed articles
SELECT 
    c.name as category,
    a.value->>'title' as article,
    (a.value->'engagement_metrics'->>'views')::int as views
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE a.value->'engagement_metrics' IS NOT NULL
ORDER BY (a.value->'engagement_metrics'->>'views')::int DESC
LIMIT 10;

-- Find external courses
SELECT 
    a.value->>'title' as title,
    a.value->'external_course_info'->>'provider' as provider
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
WHERE (a.value->'external_course_info'->>'is_external')::boolean = true;
```

### Full-Text Search

```sql
-- Search categories and content
SELECT 
    id, name, description,
    ts_rank(search_vector, query) as rank
FROM marketplace_categories,
    to_tsquery('english', 'web & development') query
WHERE search_vector @@ query
ORDER BY rank DESC;
```

---

## Elasticsearch Queries

### Basic Search

```bash
# Search all categories
curl -X GET "localhost:9200/marketplace_categories/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "match": {
        "name": "technology"
      }
    }
  }'

# Get document by ID
curl -X GET "localhost:9200/marketplace_categories/_doc/{id}?pretty"
```

### Nested Article Search

```bash
# Search articles within categories
curl -X POST "localhost:9200/marketplace_categories/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "nested": {
        "path": "sub_categories.articles",
        "query": {
          "bool": {
            "must": [
              {
                "match": {
                  "sub_categories.articles.title": "HTML"
                }
              }
            ]
          }
        },
        "inner_hits": {}
      }
    }
  }'

# Filter by tags
curl -X POST "localhost:9200/marketplace_categories/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "nested": {
        "path": "sub_categories.articles",
        "query": {
          "terms": {
            "sub_categories.articles.tags": ["html", "beginner"]
          }
        }
      }
    }
  }'
```

### Aggregations

```bash
# Count articles by difficulty
curl -X POST "localhost:9200/marketplace_categories/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "size": 0,
    "aggs": {
      "articles": {
        "nested": {
          "path": "sub_categories.articles"
        },
        "aggs": {
          "by_difficulty": {
            "terms": {
              "field": "sub_categories.articles.difficulty_level"
            }
          }
        }
      }
    }
  }'
```

---

## API Quick Reference

### cURL Examples

```bash
# Authentication
curl -X POST http://localhost:3000/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user@example.com","password":"password"}'

# Create category
curl -X POST http://localhost:3000/api/v1/marketplace/categories \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Technology",
    "slug": "technology",
    "description": "Tech category"
  }'

# Get all categories
curl -X GET "http://localhost:3000/api/v1/marketplace/categories?page=1&limit=10" \
  -H "Authorization: Bearer {token}"

# Get category by ID
curl -X GET "http://localhost:3000/api/v1/marketplace/categories/{id}" \
  -H "Authorization: Bearer {token}"

# Add subcategory
curl -X POST "http://localhost:3000/api/v1/marketplace/categories/{id}/subcategories" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Web Development",
    "slug": "web-development",
    "description": "Learn web development"
  }'

# Search
curl -X GET "http://localhost:3000/api/v1/marketplace/search?q=web+development" \
  -H "Authorization: Bearer {token}"
```

---

## JSONB Operations

### Adding Elements

```sql
-- Add subcategory to category
UPDATE marketplace_categories
SET sub_categories = sub_categories || 
    '[{
        "id": "new-uuid",
        "name": "New SubCategory",
        "slug": "new-subcategory",
        "articles": []
    }]'::jsonb
WHERE id = 'category-id';

-- Add article to subcategory (assuming first subcategory)
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles}',
    (sub_categories->0->'articles') || 
    '[{
        "id": "article-uuid",
        "title": "New Article",
        "status": "draft"
    }]'::jsonb
)
WHERE id = 'category-id';
```

### Updating Elements

```sql
-- Update subcategory name
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, name}',
    '"Updated Name"'
)
WHERE id = 'category-id';

-- Update article status
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles, 0, status}',
    '"published"'
)
WHERE id = 'category-id';

-- Update nested engagement metrics
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles, 0, engagement_metrics, views}',
    to_jsonb((sub_categories->0->'articles'->0->'engagement_metrics'->>'views')::int + 1)
)
WHERE id = 'category-id';
```

### Removing Elements

```sql
-- Remove subcategory by index
UPDATE marketplace_categories
SET sub_categories = sub_categories - 0
WHERE id = 'category-id';

-- Remove article by condition (PostgreSQL 14+)
UPDATE marketplace_categories
SET sub_categories = jsonb_set(
    sub_categories,
    '{0, articles}',
    (
        SELECT jsonb_agg(elem)
        FROM jsonb_array_elements(sub_categories->0->'articles') elem
        WHERE elem->>'id' != 'article-to-remove'
    )
)
WHERE id = 'category-id';
```

### Querying Elements

```sql
-- Check if key exists
SELECT * FROM marketplace_categories
WHERE sub_categories @> '[{"name": "Web Development"}]';

-- Query nested values
SELECT 
    sub_categories->0->>'name' as first_subcategory,
    sub_categories->0->'articles'->0->>'title' as first_article
FROM marketplace_categories
WHERE id = 'category-id';

-- Count nested elements
SELECT 
    jsonb_array_length(sub_categories) as subcategory_count,
    jsonb_array_length(sub_categories->0->'articles') as first_subcategory_articles
FROM marketplace_categories;
```

---

## Useful Commands

### PostgreSQL

```bash
# Connect to database
psql -U postgres -d marketplace_db

# List tables
\dt

# Describe table
\d marketplace_categories

# Check indexes
\di

# View table size
SELECT pg_size_pretty(pg_total_relation_size('marketplace_categories'));

# Vacuum and analyze
VACUUM ANALYZE marketplace_categories;

# Export data
COPY marketplace_categories TO '/tmp/categories.csv' CSV HEADER;

# Import data
COPY marketplace_categories FROM '/tmp/categories.csv' CSV HEADER;
```

### Elasticsearch

```bash
# Check cluster health
curl -X GET "localhost:9200/_cluster/health?pretty"

# List all indices
curl -X GET "localhost:9200/_cat/indices?v"

# Get index mapping
curl -X GET "localhost:9200/marketplace_categories/_mapping?pretty"

# Get index settings
curl -X GET "localhost:9200/marketplace_categories/_settings?pretty"

# Count documents
curl -X GET "localhost:9200/marketplace_categories/_count?pretty"

# Delete index
curl -X DELETE "localhost:9200/marketplace_categories"

# Refresh index
curl -X POST "localhost:9200/marketplace_categories/_refresh"

# Force merge
curl -X POST "localhost:9200/marketplace_categories/_forcemerge?max_num_segments=1"
```

### Docker

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Execute command in container
docker-compose exec postgres psql -U postgres -d marketplace_db

# Restart service
docker-compose restart [service-name]

# Remove volumes
docker-compose down -v
```

### Git

```bash
# Create migration
npm run migrate:create -- create_new_table

# Run migrations
npm run migrate

# Rollback migration
npm run migrate:rollback

# Check migration status
npm run migrate:status
```

---

## Performance Tips

### PostgreSQL

```sql
-- Check slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Check index usage
SELECT 
    schemaname, tablename, indexname,
    idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Analyze query plan
EXPLAIN ANALYZE
SELECT * FROM marketplace_categories
WHERE sub_categories @> '[{"name": "Web Development"}]';
```

### Elasticsearch

```bash
# Check query performance
curl -X POST "localhost:9200/marketplace_categories/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "profile": true,
    "query": {
      "match": {"name": "technology"}
    }
  }'

# Monitor cluster stats
curl -X GET "localhost:9200/_cluster/stats?pretty"

# Check node stats
curl -X GET "localhost:9200/_nodes/stats?pretty"
```

---

## Debugging Tips

### Check Data Integrity

```sql
-- Find categories with invalid JSONB
SELECT id, name
FROM marketplace_categories
WHERE NOT (sub_categories::text ~ '^[\[{]');

-- Check for orphaned subcategories
SELECT 
    c.id,
    c.name,
    sc.value->>'id' as subcategory_id
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc
WHERE sc.value->>'id' IS NULL;

-- Verify article consistency
SELECT 
    c.id,
    count(*) as article_count
FROM marketplace_categories c,
    jsonb_array_elements(c.sub_categories) sc,
    jsonb_array_elements(sc.value->'articles') a
GROUP BY c.id;
```

### Check Sync Status

```sql
-- Compare PostgreSQL and Elasticsearch counts
-- (Run in application)
const pgCount = await db.query('SELECT COUNT(*) FROM marketplace_categories');
const esCount = await es.count({ index: 'marketplace_categories' });
console.log('PG:', pgCount, 'ES:', esCount);
```

---

## Environment Variables Template

```bash
# .env
NODE_ENV=development
PORT=3000

DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=marketplace_db
DATABASE_USER=postgres
DATABASE_PASSWORD=your_password

ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=your_es_password

REDIS_HOST=localhost
REDIS_PORT=6379

JWT_SECRET=your_secret_key
JWT_EXPIRY=3600

LOG_LEVEL=debug
```

---

## Common Issues & Solutions

### Issue: JSONB index not being used

```sql
-- Solution: Ensure GIN index exists
CREATE INDEX IF NOT EXISTS idx_categories_subcategories 
ON marketplace_categories USING GIN (sub_categories);

-- Verify index usage
EXPLAIN ANALYZE
SELECT * FROM marketplace_categories
WHERE sub_categories @> '[{"name": "Web Development"}]';
```

### Issue: Elasticsearch sync failing

```bash
# Check ES cluster health
curl -X GET "localhost:9200/_cluster/health?pretty"

# Check if index exists
curl -X GET "localhost:9200/_cat/indices?v"

# Recreate index if needed
curl -X DELETE "localhost:9200/marketplace_categories"
curl -X PUT "localhost:9200/marketplace_categories" -d @mapping.json
```

### Issue: Slow JSONB queries

```sql
-- Use specific path indexes
CREATE INDEX idx_articles_by_tag 
ON marketplace_categories 
USING GIN ((sub_categories->'articles'));

-- Or use expression index
CREATE INDEX idx_article_tags 
ON marketplace_categories 
USING GIN ((
    SELECT jsonb_agg(elem->'tags')
    FROM jsonb_array_elements(sub_categories) sc,
         jsonb_array_elements(sc.value->'articles') elem
));
```

---

*Keep this guide handy for quick reference during development!*
