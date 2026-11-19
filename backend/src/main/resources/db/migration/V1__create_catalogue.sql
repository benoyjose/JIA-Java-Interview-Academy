CREATE TABLE technologies (id UUID PRIMARY KEY, slug VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(140) NOT NULL, description VARCHAR(500), sort_order INTEGER NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE categories (id UUID PRIMARY KEY, technology_id UUID NOT NULL REFERENCES technologies(id), parent_id UUID REFERENCES categories(id), slug VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_categories_technology_status_order ON categories(technology_id,status,sort_order);
CREATE TABLE lessons (id UUID PRIMARY KEY, category_id UUID NOT NULL REFERENCES categories(id), slug VARCHAR(120) NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, summary VARCHAR(700), difficulty VARCHAR(20) NOT NULL, content_json JSONB, content_revision INTEGER NOT NULL DEFAULT 1, source_type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_lessons_category_status ON lessons(category_id,status);
-- Seed taxonomy only; no lesson body is invented or published.
INSERT INTO technologies(id,slug,name,description,sort_order,status) VALUES
('10000000-0000-0000-0000-000000000001','core-java','Core Java','Java language fundamentals and core platform concepts.',1,'PUBLISHED'),
('10000000-0000-0000-0000-000000000002','advanced-java','Advanced Java','Concurrency, JVM internals and advanced language capabilities.',2,'PUBLISHED'),
('10000000-0000-0000-0000-000000000003','spring-framework','Spring Framework','Dependency injection, application context, AOP and MVC.',3,'PUBLISHED'),
('10000000-0000-0000-0000-000000000004','spring-boot','Spring Boot','Production-ready Spring applications and services.',4,'PUBLISHED'),
('10000000-0000-0000-0000-000000000005','hibernate-jpa','Hibernate and JPA','Persistence, mapping, fetching and transaction behavior.',5,'PUBLISHED'),
('10000000-0000-0000-0000-000000000006','microservices','Microservices','Distributed service architecture and operational patterns.',6,'PUBLISHED'),
('10000000-0000-0000-0000-000000000007','messaging','Messaging: Kafka and RabbitMQ','Asynchronous messaging, delivery semantics and scaling.',7,'PUBLISHED'),
('10000000-0000-0000-0000-000000000008','databases','Databases','SQL, data modeling, indexing and NoSQL systems.',8,'PUBLISHED'),
('10000000-0000-0000-0000-000000000009','system-design','System Design','High-level design, low-level design and distributed systems.',9,'PUBLISHED'),
('10000000-0000-0000-0000-000000000010','devops','DevOps and CI/CD','Build, delivery, containers and orchestration.',10,'PUBLISHED'),
('10000000-0000-0000-0000-000000000011','cloud-observability','Cloud and Observability','AWS, metrics, logs, tracing and monitoring.',11,'PUBLISHED'),
('10000000-0000-0000-0000-000000000012','engineering-excellence','Engineering Excellence','Testing, security, performance and troubleshooting.',12,'PUBLISHED');
INSERT INTO categories(id,technology_id,slug,name,sort_order,status) VALUES
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','java-language-fundamentals','Language Fundamentals',1,'PUBLISHED'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','java-collections','Collections Framework',2,'PUBLISHED'),
('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','java-streams-lambdas','Streams and Lambdas',3,'PUBLISHED'),
('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000004','spring-boot-fundamentals','Spring Boot Fundamentals',1,'PUBLISHED'),
('20000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000006','microservices-fundamentals','Microservices Fundamentals',1,'PUBLISHED');
