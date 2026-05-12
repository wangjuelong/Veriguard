package io.veriguard.database.model.contract;

/** 目标操作系统 —— 用于 §6.2 按 Linux / Windows 目标 OS 区分用例适用范围 */
public enum TargetOs {
  linux,
  windows,
  both,
  none
}
