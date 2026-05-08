package io.veriguard.utils.base;

import io.veriguard.database.model.Setting;
import io.veriguard.database.repository.SettingRepository;
import lombok.Getter;

@Getter
public abstract class ExternalServiceBase {

  public boolean serviceAvailable = false;

  public abstract SettingRepository getSettingRepository();

  protected void saveServiceState(String key, boolean state) {
    Setting setting = this.getSettingRepository().findByKey(key).orElse(new Setting(key, null));
    setting.setValue(String.valueOf(state));
    this.getSettingRepository().save(setting);
    this.serviceAvailable = state;
  }
}
