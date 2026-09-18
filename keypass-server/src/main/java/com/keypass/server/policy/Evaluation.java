package com.keypass.server.policy;

import com.keypass.common.model.RuleResult;
import java.util.List;

public record Evaluation(boolean granted, List<RuleResult> trace) {}
