include .env
export

env-up:
	docker compose up -d helpdesk-postgres

env-down:
	docker compose down helpdesk-postgres

app-run:
	./mvnw spring-boot:run

env-cleanup:
	@printf "Очистить все volume-файлы окружения? Данные будут удалены. [y/N]: "; \
	read ans; \
	if [ "$$ans" = "y" ]; then \
		docker compose down helpdesk-postgres && \
		rm -rf out/pgdata && \
		echo "Файлы окружения очищены"; \
	else \
		echo "Очистка окружения отменена"; \
	fi