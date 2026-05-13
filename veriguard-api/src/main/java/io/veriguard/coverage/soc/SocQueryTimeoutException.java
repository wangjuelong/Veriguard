package io.veriguard.coverage.soc;

/**
 * SocAdapter 查询超时 —— PR C3.
 *
 * <p>由 CoverageRunner 捕获后将该 (asset, policy) 单元格标记为 {@code timeout} 态.
 */
public class SocQueryTimeoutException extends RuntimeException {

  public SocQueryTimeoutException(String message) {
    super(message);
  }
}
