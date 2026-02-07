package com.bisioneers.medica.billing.domain;

import java.util.UUID;

public interface TenantAware {
	UUID getTenantId();
	String getTenantAlias();

}
