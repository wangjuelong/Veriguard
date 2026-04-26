package io.veriguard.utils;

import io.veriguard.ee.License;
import io.veriguard.ee.LicenseTypeEnum;

public class LicenseUtils {

  public static String computeXtmHubContractLevel(License license) {
    if (license.isLicenseEnterprise()) {
      if (license.getType() == LicenseTypeEnum.trial) {
        return "trial";
      }
      return "EE";
    }
    return "CE";
  }
}
