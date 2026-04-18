.PHONY: test build lint up down logs

test:
	cd backend && ./mvnw test
	cd frontend && npm test -- --run

build:
	cd backend && ./mvnw package -DskipTests
	cd frontend && npm run build

lint:
	cd frontend && npm run lint

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f
