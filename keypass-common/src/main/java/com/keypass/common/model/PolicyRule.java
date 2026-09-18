package com.keypass.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CurfewRule.class, name = "CURFEW"),
    @JsonSubTypes.Type(value = GeofenceRule.class, name = "GEOFENCE"),
    @JsonSubTypes.Type(value = WeekdayRule.class, name = "WEEKDAYS")
})
public sealed interface PolicyRule permits CurfewRule, GeofenceRule, WeekdayRule {
    RuleResult evaluate(AccessContext ctx);
}
