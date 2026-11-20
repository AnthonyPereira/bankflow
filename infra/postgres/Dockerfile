# Image de déploiement : le serveur ne reçoit pas les fichiers du dépôt.
FROM postgres:18.7-alpine@sha256:ff64c9e3bdf8e2120107d3559de8db314e8305f7e38c5105903c6295876738ff
COPY init/ /docker-entrypoint-initdb.d/
USER postgres
