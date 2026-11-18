# Image de déploiement : le serveur ne reçoit pas les fichiers du dépôt.
FROM apache/kafka:4.4.0
COPY create-topics.sh /scripts/create-topics.sh
ENTRYPOINT ["/bin/bash", "/scripts/create-topics.sh"]
