package com.mulesoft.connectors.typesafe.internal.extension;

import org.mule.sdk.api.annotation.Configurations;
import org.mule.sdk.api.annotation.Extension;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.meta.Category;

import com.mulesoft.connectors.typesafe.internal.config.TypeSafeConfiguration;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

/**
 * TypeSafe Connector extension entry point. It wraps TypeSafe's System One API, whose models (Jev by default) are
 * decision models: given a state and named typed questions (Noul, Choice, Score) they return one typed answer per
 * question. This connector turns those answers into first-class Mule values.
 */
@Extension(name = "TypeSafe", category = Category.SELECT)
@JavaVersionSupport(JAVA_17)
@Xml(prefix = "typesafe", namespace = "http://www.mulesoft.org/schema/mule/typesafe")
@Configurations(TypeSafeConfiguration.class)
@ErrorTypes(TypeSafeErrorType.class)
public class TypeSafeConnector {
}
