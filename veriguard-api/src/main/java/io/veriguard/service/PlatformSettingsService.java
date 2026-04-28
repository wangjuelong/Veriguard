package io.veriguard.service;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.model.SettingKeys.*;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;

import io.veriguard.config.EngineConfig;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.config.VeriguardPrincipal;
import io.veriguard.config.RabbitmqConfig;
import io.veriguard.config.cache.LicenseCacheManager;
import io.veriguard.database.model.BannerMessage;
import io.veriguard.database.model.Setting;
import io.veriguard.database.model.SettingKeys;
import io.veriguard.database.model.Theme;
import io.veriguard.database.repository.SettingRepository;
import io.veriguard.ee.Ee;
import io.veriguard.ee.License;
import io.veriguard.engine.EngineService;
import io.veriguard.expectation.ExpectationPropertiesConfig;
import io.veriguard.helper.RabbitMQHelper;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.settings.PreviewFeature;
import io.veriguard.rest.settings.form.*;
import io.veriguard.rest.settings.response.OAuthProvider;
import io.veriguard.rest.settings.response.PlatformSettings;
import io.veriguard.rest.settings.response.PublicPlatformSettings;
import io.veriguard.rest.stream.ai.AiConfig;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformSettingsService {

  public static final String THEME_TYPE_LIGHT = "light";
  public static final String THEME_TYPE_DARK = "dark";

  private final ApplicationContext context;
  private final Environment env;
  private final SettingRepository settingRepository;
  private final AiConfig aiConfig;
  private final Ee eeService;
  private final EngineService engineService;

  @Autowired private TransactionTemplate transactionTemplate;

  @Value("${veriguard.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${veriguard.mail.imap.username}")
  private String imapUsername;

  @Resource private VeriguardConfig veriguardConfig;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  @Resource private RabbitmqConfig rabbitmqConfig;
  @Resource private EngineConfig engineConfig;
  @Autowired private LicenseCacheManager licenseCacheManager;

  // -- PROVIDERS --
  private List<OAuthProvider> buildOpenIdProviders() {
    if (!this.veriguardConfig.isAuthOpenidEnable()) {
      return new ArrayList<>();
    }
    try {
      OAuth2ClientProperties properties = this.context.getBean(OAuth2ClientProperties.class);
      Map<String, OAuth2ClientProperties.Registration> providers = properties.getRegistration();
      return providers.entrySet().stream()
          .map(
              entry -> {
                String uri = "/oauth2/authorization/" + entry.getKey();
                String clientName =
                    env.getProperty("veriguard.provider." + entry.getKey() + ".login");
                // In case of missing name configuration, generate a generic name
                if (clientName == null) {
                  clientName = "Login with " + entry.getKey();
                }
                return new OAuthProvider(entry.getKey(), uri, clientName);
              })
          .toList();
    } catch (Exception e) {
      // No provider defined in the configuration
      return new ArrayList<>();
    }
  }

  private List<OAuthProvider> buildSaml2Providers() {
    if (!this.veriguardConfig.isAuthSaml2Enable()) {
      return new ArrayList<>();
    }
    try {
      Saml2RelyingPartyProperties properties =
          this.context.getBean(Saml2RelyingPartyProperties.class);
      Map<String, Saml2RelyingPartyProperties.Registration> providers =
          properties.getRegistration();
      return providers.entrySet().stream()
          .map(
              entry -> {
                String uri = "/saml2/authenticate/" + entry.getKey();
                String clientName =
                    env.getProperty("veriguard.provider." + entry.getKey() + ".login");
                // In case of missing name configuration, generate a generic name
                if (clientName == null) {
                  clientName = "Login with " + entry.getKey();
                }
                return new OAuthProvider(entry.getKey(), uri, clientName);
              })
          .toList();
    } catch (Exception e) {
      // No provider defined in the configuration
      return new ArrayList<>();
    }
  }

  // -- MAP UTILS --
  private Map<String, Setting> mapOfSettings(@NotNull List<Setting> settings) {
    return settings.stream().collect(Collectors.toMap(Setting::getKey, Function.identity()));
  }

  private String getValueFromMapOfSettings(
      @NotNull Map<String, Setting> dbSettings, @NotBlank final String key) {
    return Optional.ofNullable(dbSettings.get(key)).map(Setting::getValue).orElse(null);
  }

  private Setting resolveFromMap(Map<String, Setting> dbSettings, String themeKey, String value) {
    Optional<Setting> optionalSetting = ofNullable(dbSettings.get(themeKey));
    return resolve(optionalSetting, themeKey, value);
  }

  private Setting resolve(Optional<Setting> optionalSetting, String themeKey, String value) {
    if (optionalSetting.isPresent()) {
      Setting updateSetting = optionalSetting.get();
      updateSetting.setValue(value);
      return updateSetting;
    }
    return new Setting(themeKey, value);
  }

  /**
   * Save setting
   *
   * @param setting setting to save
   * @return setting saved
   */
  public Setting save(Setting setting) {
    return this.settingRepository.save(setting);
  }

  // -- FIND SETTINGS --

  /** Populate the public (non-sensitive) fields on any {@link PublicPlatformSettings} instance. */
  private void populatePublicSettings(
      PublicPlatformSettings settings, Map<String, Setting> dbSettings) {
    // Auth providers
    settings.setPlatformOpenIdProviders(buildOpenIdProviders());
    settings.setPlatformSaml2Providers(buildSaml2Providers());
    settings.setAuthOpenidEnable(veriguardConfig.isAuthOpenidEnable());
    settings.setAuthSaml2Enable(veriguardConfig.isAuthSaml2Enable());
    settings.setAuthLocalEnable(veriguardConfig.isAuthLocalEnable());

    // Theme & language
    settings.setPlatformTheme(
        ofNullable(dbSettings.get(DEFAULT_THEME.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_THEME.defaultValue()));
    settings.setPlatformLang(
        ofNullable(dbSettings.get(DEFAULT_LANG.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_LANG.defaultValue()));
    settings.setThemeLight(createThemeInput(dbSettings, THEME_TYPE_LIGHT));
    settings.setThemeDark(createThemeInput(dbSettings, THEME_TYPE_DARK));

    // Policies
    PolicyInput policies = new PolicyInput();
    policies.setLoginMessage(getValueFromMapOfSettings(dbSettings, PLATFORM_LOGIN_MESSAGE.key()));
    policies.setConsentMessage(
        getValueFromMapOfSettings(dbSettings, PLATFORM_CONSENT_MESSAGE.key()));
    policies.setConsentConfirmText(
        getValueFromMapOfSettings(dbSettings, PLATFORM_CONSENT_CONFIRM_TEXT.key()));
    settings.setPolicies(policies);

    // Feature flags
    if (!StringUtils.hasText(veriguardConfig.getEnabledDevFeatures())) {
      settings.setEnabledDevFeatures(new ArrayList<>());
    } else {
      settings.setEnabledDevFeatures(
          Arrays.stream(veriguardConfig.getEnabledDevFeatures().split(","))
              .map(
                  featureStr -> {
                    try {
                      return PreviewFeature.fromStringIgnoreCase(featureStr.strip());
                    } catch (IllegalArgumentException e) {
                      log.warn(String.format("Unrecognised feature flag: %s", e.getMessage()), e);
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .distinct()
              .toList());
    }

    // Platform banners
    Map<String, List<String>> platformBannerByLevel = new HashMap<>();
    for (BannerMessage.BANNER_KEYS bannerKey : BannerMessage.BANNER_KEYS.values()) {
      String value = getValueFromMapOfSettings(dbSettings, PLATFORM_BANNER + "." + bannerKey.key());
      if (value != null) {
        if (platformBannerByLevel.get(bannerKey.level().name()) == null) {
          platformBannerByLevel.put(
              bannerKey.level().name(), new ArrayList<>(Arrays.asList(bannerKey.message())));
        } else {
          platformBannerByLevel.get(bannerKey.level().name()).add(bannerKey.message());
        }
      }
    }
    settings.setPlatformBannerByLevel(platformBannerByLevel);

    // Whitemark
    settings.setPlatformWhitemark(
        ofNullable(dbSettings.get(PLATFORM_WHITEMARK.key()))
            .map(Setting::getValue)
            .orElse(PLATFORM_WHITEMARK.defaultValue()));
  }

  /** Return only non-sensitive settings suitable for unauthenticated (public) access. */
  public PublicPlatformSettings findPublicSettings() {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    PublicPlatformSettings settings = new PublicPlatformSettings();
    populatePublicSettings(settings, dbSettings);
    return settings;
  }

  /** Return the full platform settings. Must only be called from authenticated endpoints. */
  public PlatformSettings findSettings() {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    PlatformSettings platformSettings = new PlatformSettings();

    // Populate public fields (shared with findPublicSettings)
    populatePublicSettings(platformSettings, dbSettings);

    // Authenticated-only fields
    platformSettings.setPlatformLicense(licenseCacheManager.getEnterpriseEditionInfo());
    platformSettings.setPlatformHomeDashboard(
        ofNullable(dbSettings.get(DEFAULT_HOME_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_HOME_DASHBOARD.defaultValue()));
    platformSettings.setPlatformScenarioDashboard(
        ofNullable(dbSettings.get(DEFAULT_SCENARIO_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_SCENARIO_DASHBOARD.defaultValue()));
    platformSettings.setPlatformSimulationDashboard(
        ofNullable(dbSettings.get(DEFAULT_SIMULATION_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_SIMULATION_DASHBOARD.defaultValue()));
    if (this.imapEnabled) {
      platformSettings.setDefaultMailer(this.imapUsername);
      platformSettings.setDefaultReplyTo(this.imapUsername);
    } else {
      platformSettings.setDefaultMailer(veriguardConfig.getDefaultMailer());
      platformSettings.setDefaultReplyTo(veriguardConfig.getDefaultReplyTo());
    }
    platformSettings.setSmtpServiceAvailable(
        ofNullable(dbSettings.get(SMTP_SERVICE_AVAILABLE.key()))
            .map(Setting::getValue)
            .orElse(SMTP_SERVICE_AVAILABLE.defaultValue()));
    platformSettings.setImapServiceAvailable(
        ofNullable(dbSettings.get(IMAP_SERVICE_AVAILABLE.key()))
            .map(Setting::getValue)
            .orElse(IMAP_SERVICE_AVAILABLE.defaultValue()));

    // Authenticated user settings
    platformSettings.setMapTileServerLight(veriguardConfig.getMapTileServerLight());
    platformSettings.setMapTileServerDark(veriguardConfig.getMapTileServerDark());
    platformSettings.setPlatformId(
        ofNullable(dbSettings.get(PLATFORM_INSTANCE.key()))
            .map(Setting::getValue)
            .orElse(PLATFORM_INSTANCE.defaultValue()));
    platformSettings.setPlatformName(
        ofNullable(dbSettings.get(PLATFORM_NAME.key()))
            .map(Setting::getValue)
            .orElse(PLATFORM_NAME.defaultValue()));
    platformSettings.setPlatformBaseUrl(veriguardConfig.getBaseUrl());
    platformSettings.setPlatformAgentUrl(veriguardConfig.getBaseUrlForAgent());
    platformSettings.setAiEnabled(aiConfig.isEnabled());
    platformSettings.setAiHasToken(StringUtils.hasText(aiConfig.getToken()));
    platformSettings.setAiType(aiConfig.getType());
    platformSettings.setAiModel(aiConfig.getModel());
    platformSettings.setExecutorTaniumEnable(false);
    platformSettings.setTelemetryManagerEnable(true);

    // Admin-only settings
    VeriguardPrincipal user = currentUser();
    if (user != null && user.isAdmin()) {
      platformSettings.setPlatformVersion(veriguardConfig.getVersion());
      platformSettings.setPostgreVersion(settingRepository.getServerVersion());
      platformSettings.setJavaVersion(Runtime.version().toString());
      platformSettings.setRabbitMQVersion(RabbitMQHelper.getRabbitMQVersion(rabbitmqConfig));
      platformSettings.setAnalyticsEngineType(engineConfig.getEngineSelector());
      platformSettings.setAnalyticsEngineVersion(engineService.getEngineVersion());
    }

    // EXPECTATION
    platformSettings.setDetectionExpirationTime(
        expectationPropertiesConfig.getDetectionExpirationTime());
    platformSettings.setPreventionExpirationTime(
        expectationPropertiesConfig.getPreventionExpirationTime());
    platformSettings.setVulnerabilityExpirationTime(
        expectationPropertiesConfig.getVulnerabilityExpirationTime());
    platformSettings.setChallengeExpirationTime(
        expectationPropertiesConfig.getChallengeExpirationTime());
    platformSettings.setArticleExpirationTime(
        expectationPropertiesConfig.getArticleExpirationTime());
    platformSettings.setManualExpirationTime(expectationPropertiesConfig.getManualExpirationTime());
    platformSettings.setExpectationDefaultScoreValue(
        expectationPropertiesConfig.getDefaultExpectationScoreValue());

    return platformSettings;
  }

  /**
   * Get platform version
   *
   * @return platform version
   */
  public String getPlatformVersion() {
    return veriguardConfig.getVersion();
  }

  public Map<String, Setting> findSettingsByKeys(List<String> keys) {
    return mapOfSettings(fromIterable(this.settingRepository.findAllByKeyIn(keys)));
  }

  private ThemeInput createThemeInput(Map<String, Setting> dbSettings, String themeType) {
    ThemeInput themeInput = new ThemeInput();
    themeInput.setBackgroundColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.BACKGROUND_COLOR.key()));
    themeInput.setPaperColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.PAPER_COLOR.key()));
    themeInput.setNavigationColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.NAVIGATION_COLOR.key()));
    themeInput.setPrimaryColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.PRIMARY_COLOR.key()));
    themeInput.setSecondaryColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.SECONDARY_COLOR.key()));
    themeInput.setAccentColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.ACCENT_COLOR.key()));
    themeInput.setLogoUrl(
        getValueFromMapOfSettings(dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL.key()));
    themeInput.setLogoLoginUrl(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_LOGIN_URL.key()));
    themeInput.setLogoUrlCollapsed(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL_COLLAPSED.key()));
    return themeInput;
  }

  // -- UPDATE SETTINGS --
  public Optional<Setting> setting(String key) {
    return this.settingRepository.findByKey(key);
  }

  private void addSettingIfExists(
      List<Setting> settingsToSave, Map<String, Setting> dbSettings, String key, String value) {
    if (value != null) {
      settingsToSave.add(resolveFromMap(dbSettings, key, value));
    }
  }

  public PlatformSettings updateBasicConfigurationSettings(SettingsUpdateInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(resolveFromMap(dbSettings, PLATFORM_NAME.key(), input.getName()));
    settingsToSave.add(resolveFromMap(dbSettings, DEFAULT_THEME.key(), input.getTheme()));
    settingsToSave.add(resolveFromMap(dbSettings, DEFAULT_LANG.key(), input.getLang()));
    addSettingIfExists(
        settingsToSave, dbSettings, DEFAULT_HOME_DASHBOARD.key(), input.getHomeDashboard());
    addSettingIfExists(
        settingsToSave, dbSettings, DEFAULT_SCENARIO_DASHBOARD.key(), input.getScenarioDashboard());
    addSettingIfExists(
        settingsToSave,
        dbSettings,
        DEFAULT_SIMULATION_DASHBOARD.key(),
        input.getSimulationDashboard());
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateSettingsEnterpriseEdition(
      SettingsEnterpriseEditionUpdateInput input) throws Exception {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    String certPem = input.getEnterpriseEdition();
    if (certPem != null && !certPem.isEmpty()) {
      License license = eeService.verifyCertificate(certPem);
      if (!license.isLicenseValidated()) {
        throw new BadRequestException("Invalid certificate");
      }
    }
    settingsToSave.add(resolveFromMap(dbSettings, PLATFORM_ENTERPRISE_LICENSE.key(), certPem));
    settingRepository.saveAll(settingsToSave);
    licenseCacheManager.refreshLicense();
    return findSettings();
  }

  public PlatformSettings updateSettingsPlatformWhitemark(
      SettingsPlatformWhitemarkUpdateInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_WHITEMARK.key(), input.getPlatformWhitemark()));
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateSettingsPolicies(PolicyInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_LOGIN_MESSAGE.key(), input.getLoginMessage()));
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_CONSENT_MESSAGE.key(), input.getConsentMessage()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings, PLATFORM_CONSENT_CONFIRM_TEXT.key(), input.getConsentConfirmText()));
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateThemeLight(ThemeInput input) {
    return updateTheme(input, THEME_TYPE_LIGHT);
  }

  public PlatformSettings updateThemeDark(ThemeInput input) {
    return updateTheme(input, THEME_TYPE_DARK);
  }

  /**
   * Clear default platform dashboard settings if they match the provided dashboardId.
   *
   * @param dashboardId the dashboard ID to check against default settings
   */
  public void clearDefaultPlatformDashboardIfMatch(String dashboardId) {
    List<SettingKeys> clearableSettings =
        List.of(SettingKeys.DEFAULT_SCENARIO_DASHBOARD, SettingKeys.DEFAULT_SIMULATION_DASHBOARD);

    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));

    List<Setting> settingsToSave = new ArrayList<>();
    clearableSettings.forEach(
        setting -> {
          String currentValue = getValueFromMapOfSettings(dbSettings, setting.key());
          if (dashboardId.equals(currentValue)) {
            settingsToSave.add(resolveFromMap(dbSettings, setting.key(), setting.defaultValue()));
          }
        });
    settingRepository.saveAll(settingsToSave);
  }

  private PlatformSettings updateTheme(ThemeInput input, String themeType) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();

    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.BACKGROUND_COLOR.key(),
            input.getBackgroundColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.PAPER_COLOR.key(),
            input.getPaperColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.NAVIGATION_COLOR.key(),
            input.getNavigationColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.PRIMARY_COLOR.key(),
            input.getPrimaryColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.SECONDARY_COLOR.key(),
            input.getSecondaryColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.ACCENT_COLOR.key(),
            input.getAccentColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL.key(), input.getLogoUrl()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.LOGO_URL_COLLAPSED.key(),
            input.getLogoUrlCollapsed()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.LOGO_LOGIN_URL.key(),
            input.getLogoLoginUrl()));

    List<Setting> update = new ArrayList<>();
    List<String> delete = new ArrayList<>();
    settingsToSave.forEach(
        setting -> {
          if (StringUtils.hasText(setting.getValue())) {
            update.add(setting);
          } else if (StringUtils.hasText(setting.getId())) {
            delete.add(setting.getId());
          }
        });

    settingRepository.deleteAllById(delete);
    settingRepository.saveAll(update);
    return findSettings();
  }

  /**
   * Saves a map of settings
   *
   * @param settingsMap map of settings to save
   * @return map of settings saved
   */
  public Map<String, Setting> saveSettings(Map<String, String> settingsMap) {
    Map<String, Setting> dbSettings =
        this.findSettingsByKeys(new ArrayList<>(settingsMap.keySet()));

    List<Setting> settingsToSave = new ArrayList<>();
    settingsMap.forEach(
        (key, value) -> {
          settingsToSave.add(resolveFromMap(dbSettings, key, value));
        });

    return mapOfSettings(fromIterable(this.settingRepository.saveAll(settingsToSave)));
  }

  /**
   * Saves a setting by key. Updates the value if the key exists, creates a new setting otherwise.
   *
   * @param key the setting key
   * @param value the setting value
   * @return the saved setting
   */
  public Setting saveSetting(String key, String value) {
    Setting setting = settingRepository.findByKey(key).orElse(new Setting(key, value));
    setting.setValue(value);
    return settingRepository.save(setting);
  }

  // -- PLATFORM MESSAGE --

  public void cleanMessage(@NotBlank final BannerMessage.BANNER_KEYS banner) {
    settingRepository.deleteByKeyIn(List.of(PLATFORM_BANNER + "." + banner.key()));
  }

  public void errorMessage(@NotBlank final BannerMessage.BANNER_KEYS banner) {
    Optional<Setting> bannerLevelOpt =
        this.settingRepository.findByKey(PLATFORM_BANNER + "." + banner.key());
    if (bannerLevelOpt.isEmpty()) {
      Setting bannerLevel =
          resolve(bannerLevelOpt, PLATFORM_BANNER + "." + banner.key(), banner.level().name());
      settingRepository.save(bannerLevel);
    }
  }

  public boolean isPlatformWhiteMarked() {
    String defaultValue = SettingKeys.PLATFORM_WHITEMARK.defaultValue();
    Optional<Setting> platformWhiteMarkedSetting =
        this.setting(SettingKeys.PLATFORM_WHITEMARK.name().toLowerCase());
    return platformWhiteMarkedSetting
        .map(setting -> parseBoolean(setting.getValue()))
        .orElse(parseBoolean(defaultValue));
  }
}
