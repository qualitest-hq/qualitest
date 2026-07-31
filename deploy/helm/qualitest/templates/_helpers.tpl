{{/*
Expand the name of the chart.
*/}}
{{- define "qualitest.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "qualitest.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "qualitest.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "qualitest.labels" -}}
helm.sh/chart: {{ include "qualitest.chart" . }}
{{ include "qualitest.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "qualitest.selectorLabels" -}}
app.kubernetes.io/name: {{ include "qualitest.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "qualitest.appFullname" -}}
{{ include "qualitest.fullname" . }}-app
{{- end }}

{{- define "qualitest.webFullname" -}}
{{ include "qualitest.fullname" . }}-web
{{- end }}

{{- define "qualitest.mysqlFullname" -}}
{{ include "qualitest.fullname" . }}-mysql
{{- end }}

{{- define "qualitest.redisFullname" -}}
{{ include "qualitest.fullname" . }}-redis
{{- end }}

{{- define "qualitest.secretName" -}}
{{ include "qualitest.fullname" . }}-secrets
{{- end }}

{{/* JDBC URL when using bundled MySQL */}}
{{- define "qualitest.jdbcUrl" -}}
{{- if and .Values.mysql.enabled (eq .Values.app.external.jdbcUrl "") -}}
jdbc:mysql://{{ include "qualitest.mysqlFullname" . }}:3306/{{ .Values.mysql.database }}?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8
{{- else -}}
{{- .Values.app.external.jdbcUrl -}}
{{- end -}}
{{- end }}

{{- define "qualitest.redisHost" -}}
{{- if and .Values.redis.enabled (eq .Values.app.external.redisHost "") -}}
{{ include "qualitest.redisFullname" . }}
{{- else -}}
{{- .Values.app.external.redisHost -}}
{{- end -}}
{{- end }}
