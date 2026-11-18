# Image de déploiement : le serveur ne reçoit pas les fichiers du dépôt.
FROM postgres:18.7-alpine
COPY init/ /docker-entrypoint-initdb.d/
USER postgres
