FROM quay.io/prometheus/alertmanager:v0.32.1

USER root
COPY --chmod=0444 deploy/observability/alertmanager/alertmanager.yml.template /etc/alertmanager/alertmanager.yml.template
COPY --chmod=0555 deploy/observability/alertmanager/entrypoint.sh /usr/local/bin/novel-alertmanager
USER 65534

ENTRYPOINT ["/usr/local/bin/novel-alertmanager"]
