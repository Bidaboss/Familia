// The real MonitoringReader of récit 1 sexies: the Cloud Monitoring REST API (v3), called with the
// token of the functions' service account. That account needs the « Lecteur Monitoring » role
// (roles/monitoring.viewer); without it, the calls fail and the day is kept with an error.

import { applicationDefault } from 'firebase-admin/app';
import { MonitoringReader } from './consumption';

const API = 'https://monitoring.googleapis.com/v3';

function projectId(): string {
  const fromConfig = process.env.FIREBASE_CONFIG ? (JSON.parse(process.env.FIREBASE_CONFIG).projectId as string | undefined) : undefined;
  const id = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT ?? fromConfig;
  if (!id) throw new Error('No project id');
  return id;
}

async function get(path: string, params: Record<string, string>): Promise<Record<string, unknown>> {
  const token = (await applicationDefault().getAccessToken()).access_token;
  const url = `${API}/projects/${projectId()}/${path}?${new URLSearchParams(params)}`;
  const response = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`Monitoring ${response.status}`);
  return (await response.json()) as Record<string, unknown>;
}

interface Point {
  value?: { int64Value?: string; doubleValue?: number };
}

export const cloudMonitoring: MonitoringReader = {
  async metricTypes() {
    const types: string[] = [];
    let pageToken = '';
    do {
      const page = await get('metricDescriptors', {
        filter: 'metric.type = starts_with("firestore.googleapis.com/")',
        ...(pageToken ? { pageToken } : {}),
      });
      for (const d of (page.metricDescriptors as { type: string }[] | undefined) ?? []) types.push(d.type);
      pageToken = (page.nextPageToken as string | undefined) ?? '';
    } while (pageToken);
    return types;
  },

  async total(metricType, start, end, gauge) {
    const seconds = Math.round((end.getTime() - start.getTime()) / 1000);
    const page = await get('timeSeries', {
      filter: `metric.type = "${metricType}"`,
      'interval.startTime': start.toISOString(),
      'interval.endTime': end.toISOString(),
      'aggregation.alignmentPeriod': `${seconds}s`,
      'aggregation.perSeriesAligner': gauge ? 'ALIGN_MAX' : 'ALIGN_SUM',
      'aggregation.crossSeriesReducer': gauge ? 'REDUCE_MAX' : 'REDUCE_SUM',
    });
    const series = (page.timeSeries as { points?: Point[] }[] | undefined) ?? [];
    const values = series.flatMap((s) => s.points ?? []).map((p) => Number(p.value?.int64Value ?? p.value?.doubleValue ?? 0));
    if (values.length === 0) return null;
    return gauge ? Math.max(...values) : values.reduce((a, b) => a + b, 0);
  },
};
