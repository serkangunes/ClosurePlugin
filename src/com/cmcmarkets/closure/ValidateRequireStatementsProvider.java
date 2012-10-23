package com.cmcmarkets.closure;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * This document and its contents are protected by copyright 2011 and owned by CMC Markets UK Plc.
 * The copying and reproduction of this document and/or its content (whether wholly or partly) or any
 * incorporation of the same into any other material in any media or format of any kind is strictly prohibited.
 * All rights are reserved.
 * <p/>
 * © CMC Markets Plc 2012
 */
public class ValidateRequireStatementsProvider implements InspectionToolProvider
{
    public Class[] getInspectionClasses()
    {
        return new Class[]{ValidateRequireStatementsInspection.class};
    }
}
