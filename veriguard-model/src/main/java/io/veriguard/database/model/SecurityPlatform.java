package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.stix.objects.DomainObject;
import io.veriguard.stix.objects.constants.CommonProperties;
import io.veriguard.stix.objects.constants.ObjectTypes;
import io.veriguard.stix.parsing.StixDomainObjectConvertible;
import io.veriguard.stix.types.Identifier;
import io.veriguard.stix.types.StixString;
import io.veriguard.stix.types.Timestamp;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.SECURITY_PLATFORM_TYPE)
@EntityListeners(ModelBaseListener.class)
public class SecurityPlatform extends Asset implements StixDomainObjectConvertible {

  @Override
  public DomainObject toStixDomainObject() {
    return new DomainObject(
        new HashMap<>(
            Map.of(
                CommonProperties.ID.toString(),
                new Identifier(ObjectTypes.IDENTITY.toString(), this.getId()),
                CommonProperties.CREATED.toString(),
                new Timestamp(this.getCreatedAt()),
                CommonProperties.MODIFIED.toString(),
                new Timestamp(this.getUpdatedAt()),
                "name",
                new StixString(this.getName()),
                CommonProperties.TYPE.toString(),
                new StixString(ObjectTypes.IDENTITY.toString()),
                "identity_class",
                new StixString("securityplatform"))));
  }

  public enum SECURITY_PLATFORM_TYPE {
    @JsonProperty("EDR")
    EDR,
    @JsonProperty("XDR")
    XDR,
    @JsonProperty("SIEM")
    SIEM,
    @JsonProperty("SOAR")
    SOAR,
    @JsonProperty("NDR")
    NDR,
    @JsonProperty("ISPM")
    ISPM,
  }

  @Queryable(filterable = true, sortable = true)
  @Column(name = "security_platform_type")
  @JsonProperty("security_platform_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private SECURITY_PLATFORM_TYPE securityPlatformType;

  @OneToMany(
      mappedBy = "securityPlatform",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("security_platform_traces")
  private List<NodeExpectationTrace> traces;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "security_platform_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("security_platform_logo_light")
  @Schema(type = "string")
  private Document logoLight;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "security_platform_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("security_platform_logo_dark")
  @Schema(type = "string")
  private Document logoDark;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SECURITY_PLATFORM;

  public SecurityPlatform() {}

  public SecurityPlatform(
      String id, String type, String name, SECURITY_PLATFORM_TYPE securityPlatformType) {
    super(id, type, name);
    this.securityPlatformType = securityPlatformType;
  }
}
