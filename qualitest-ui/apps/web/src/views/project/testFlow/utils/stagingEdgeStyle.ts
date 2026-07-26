/**
 * Staging 边 stroke 样式（DefaultEdge / ConditionEdge 共用）。
 */
import type { AiStagingCanvasMark } from '../types/aiStagingTypes';
import {
  STAGING_DELETE_COLOR,
  STAGING_ERROR_COLOR,
  STAGING_UPDATE_COLOR,
} from '../constants/stagingTheme';

export interface StagingEdgeStrokeStyle {
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
}

export function resolveStagingEdgeStroke(
  mark: AiStagingCanvasMark | undefined,
  hasError: boolean,
  selected: boolean,
): StagingEdgeStrokeStyle | null {
  if (hasError) {
    return { stroke: `rgba(220, 38, 38, 0.9)`, strokeWidth: 2.5 };
  }
  if (mark?.mode === 'delete') {
    return { stroke: `rgba(185, 28, 28, 0.85)`, strokeWidth: 2.5, strokeDasharray: '5 4' };
  }
  if (mark?.mode === 'update') {
    return { stroke: `rgba(11, 110, 220, 0.9)`, strokeWidth: 2.5 };
  }
  if (mark?.mode === 'add') {
    return { stroke: `rgba(124, 58, 237, 0.9)`, strokeWidth: 2.5, strokeDasharray: '6 4' };
  }
  if (selected) {
    return { stroke: STAGING_UPDATE_COLOR, strokeWidth: 2.5 };
  }
  return null;
}

export function defaultEdgeStroke(selected: boolean): StagingEdgeStrokeStyle {
  return {
    stroke: selected ? STAGING_UPDATE_COLOR : '#94a3b8',
    strokeWidth: selected ? 2.5 : 1.5,
  };
}
