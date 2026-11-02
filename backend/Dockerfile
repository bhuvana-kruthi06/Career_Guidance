FROM eclipse-temurin:24-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY backend/target/career-guidance-api-0.1.0.jar /app/app.jar
COPY backend/matcher_cli.py /app/backend/matcher_cli.py
COPY data_processing /app/data_processing
COPY data/resources/skill_resources.json /app/data/resources/skill_resources.json
COPY data/projects/project_catalog.json /app/data/projects/project_catalog.json
COPY backend/requirements-runtime.txt /app/requirements-runtime.txt
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir -r /app/requirements-runtime.txt \
    && useradd --system --uid 10001 --create-home appuser \
    && chown -R appuser:appuser /app /opt/venv
USER 10001
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
