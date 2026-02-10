# CIOS Content Service - Marketplace Category System

## Overview

The CIOS Content Service is a comprehensive platform for managing marketplace content with a hierarchical structure: **Category → SubCategory → Articles**. The system leverages PostgreSQL with JSONB for flexible data storage and Elasticsearch for powerful search capabilities.

## Features

- **Hierarchical Content Structure**: Three-level hierarchy (Category → SubCategory → Articles)
- **Dual Storage Architecture**: PostgreSQL (source of truth) + Elasticsearch (search optimization)
- **JSONB Support**: Flexible nested data structures in PostgreSQL
- **Full-Text Search**: Advanced search capabilities with Elasticsearch
- **Progress Tracking**: Track user learning progress across external courses
- **Partner Integration**: SSO integration and partner registration for marketplace
- **RESTful API**: Comprehensive API for content management
- **Scalable Design**: Designed for high availability and horizontal scaling

## Documentation

### Core Documentation

- **[Design Documentation](./MARKETPLACE_CATEGORY_DESIGN.md)** - Complete system design including architecture, data models, and specifications
- **[Database Schema](./docs/DATABASE_SCHEMA.md)** - Detailed database schema with ERDs and JSONB structures
- **[API Examples](./docs/API_EXAMPLES.md)** - Comprehensive API examples and use cases
- **[Implementation Guide](./docs/IMPLEMENTATION_GUIDE.md)** - Step-by-step implementation instructions

### Related Resources

- [User Story KB-12838](https://karmayogibharat.atlassian.net/browse/KB-12838)
- [Figma Design](https://figma.com/design/bloj5l1lZIm2oIyDxf1Jr1/Marketplace?node-id=3103-5181&t=HtvdbrkH39DbvsyU-0)
- [Marketplace SSO Documentation](https://karmayogibharat.atlassian.net/wiki/spaces/TES/pages/605093889/Market+place+SSO+integration+and+partner+registration)
- [External Courses Progress](https://karmayogibharat.atlassian.net/wiki/spaces/TES/pages/413171733/External+courses+Progress+Update)

## Quick Start

### Prerequisites

- PostgreSQL 12+
- Elasticsearch 7.x or 8.x
- Node.js 16+ (or your preferred backend)
- Docker (optional)

### Installation

```bash
# Clone the repository
git clone https://github.com/ArpithaSureshappa/cios-content-service.git
cd cios-content-service

# Install dependencies
npm install

# Set up environment variables
cp .env.example .env
# Edit .env with your configuration

# Run database migrations
npm run migrate

# Start the service
npm start
```

### Using Docker

```bash
# Start all services (PostgreSQL, Elasticsearch, Redis)
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## Architecture

### System Architecture

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

### Data Model

```
Category (Root)
├── id, name, slug, description
├── metadata (JSONB)
└── subCategories (JSONB Array)
    ├── SubCategory
    │   ├── id, name, slug, description
    │   ├── metadata (JSONB)
    │   └── articles (JSONB Array)
    │       └── Article
    │           ├── id, title, content_url
    │           ├── author, tags, status
    │           ├── external_course_info (JSONB)
    │           ├── engagement_metrics (JSONB)
    │           └── timestamps
    └── ...
```

## API Overview

### Category Endpoints

```http
POST   /api/v1/marketplace/categories              # Create category
GET    /api/v1/marketplace/categories              # List categories
GET    /api/v1/marketplace/categories/:id          # Get category
PUT    /api/v1/marketplace/categories/:id          # Update category
DELETE /api/v1/marketplace/categories/:id          # Delete category
```

### SubCategory Endpoints

```http
POST   /api/v1/marketplace/categories/:id/subcategories              # Add subcategory
GET    /api/v1/marketplace/categories/:id/subcategories              # List subcategories
PUT    /api/v1/marketplace/categories/:id/subcategories/:subId       # Update subcategory
DELETE /api/v1/marketplace/categories/:id/subcategories/:subId       # Delete subcategory
```

### Article Endpoints

```http
POST   /api/v1/marketplace/categories/:catId/subcategories/:subId/articles    # Add article
GET    /api/v1/marketplace/articles/:id                                       # Get article
PUT    /api/v1/marketplace/articles/:id                                       # Update article
DELETE /api/v1/marketplace/articles/:id                                       # Delete article
```

### Search Endpoints

```http
GET    /api/v1/marketplace/search                  # Global search
GET    /api/v1/marketplace/search/articles         # Search articles
GET    /api/v1/marketplace/search/autocomplete     # Autocomplete
```

### Progress Tracking

```http
POST   /api/v1/marketplace/progress                # Update progress
GET    /api/v1/marketplace/progress                # Get user progress
POST   /api/v1/marketplace/progress/complete       # Mark complete
```

## Technology Stack

- **Database**: PostgreSQL 12+ with JSONB support
- **Search Engine**: Elasticsearch 7.x/8.x
- **Cache**: Redis (optional)
- **Backend**: Node.js / Python / Java (flexible)
- **API Style**: RESTful / GraphQL
- **Deployment**: Docker, Kubernetes

## Key Features

### JSONB Nested Structure

Store hierarchical data efficiently in PostgreSQL:

```json
{
  "sub_categories": [
    {
      "id": "uuid",
      "name": "Web Development",
      "articles": [
        {
          "id": "uuid",
          "title": "Introduction to HTML",
          "tags": ["html", "beginner"],
          "engagement_metrics": {
            "views": 1250,
            "likes": 98
          }
        }
      ]
    }
  ]
}
```

### Powerful Queries

```sql
-- Search articles by tag
SELECT c.name, a.value->>'title'
FROM marketplace_categories c,
     jsonb_array_elements(c.sub_categories) sc,
     jsonb_array_elements(sc.value->'articles') a
WHERE a.value->'tags' ? 'html';
```

### Elasticsearch Integration

```json
{
  "query": {
    "nested": {
      "path": "sub_categories.articles",
      "query": {
        "match": {
          "sub_categories.articles.title": "HTML"
        }
      }
    }
  }
}
```

## Development

### Project Structure

```
cios-content-service/
├── src/
│   ├── config/          # Configuration
│   ├── models/          # Data models
│   ├── controllers/     # API controllers
│   ├── services/        # Business logic
│   ├── repositories/    # Data access
│   ├── routes/          # API routes
│   └── index.js         # Entry point
├── db/
│   └── migrations/      # Database migrations
├── elasticsearch/
│   └── mappings/        # ES mappings
├── tests/               # Tests
├── docs/                # Documentation
└── docker-compose.yml   # Docker setup
```

### Running Tests

```bash
# Run all tests
npm test

# Run with coverage
npm run test:coverage

# Run specific tests
npm test -- tests/unit/categoryService.test.js
```

### Database Migrations

```bash
# Create new migration
npm run migrate:create -- create_categories_table

# Run migrations
npm run migrate

# Rollback
npm run migrate:rollback
```

## Deployment

### Docker Deployment

```bash
# Build image
docker build -t cios-content-service:latest .

# Run container
docker run -d -p 3000:3000 --env-file .env cios-content-service:latest
```

### Kubernetes Deployment

```bash
# Apply configuration
kubectl apply -f k8s/

# Check deployment
kubectl get pods
kubectl get services

# View logs
kubectl logs -f deployment/content-service
```

## Monitoring

### Health Check

```bash
curl http://localhost:3000/health
```

### Metrics

- Database connection pool status
- Elasticsearch cluster health
- API response times
- Error rates

## Security

- JWT-based authentication
- Role-based access control (RBAC)
- API rate limiting
- Input validation and sanitization
- SQL injection prevention
- Encryption at rest and in transit

## Performance

- PostgreSQL JSONB indexing with GIN indexes
- Elasticsearch nested queries optimization
- Redis caching layer
- Connection pooling
- Query optimization
- Horizontal scaling support

## Contributing

Please read our contributing guidelines before submitting pull requests.

## Support

For issues and questions:
- Create an issue on GitHub
- Contact the development team
- Check the documentation

## License

[Your License Here]

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for version history.

---

**Version**: 1.0.0  
**Last Updated**: 2024-02-10  
**Maintainers**: Content Service Team
