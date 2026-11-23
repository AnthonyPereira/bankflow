# Image de déploiement : le serveur ne reçoit pas les fichiers du dépôt.
FROM apache/kafka:4.4.0@sha256:833b867a9585a141da455dc2b57431c65cbccb27f11c91f694444cca9f777e0f
COPY create-topics.sh /scripts/create-topics.sh
USER appuser
ENTRYPOINT ["/bin/bash", "/scripts/create-topics.sh"]
