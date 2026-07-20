package com.ecoexpress.catalog.domain;

/**
 * Kinds of certificate a product can carry (V9). India-first: NPOP/India Organic and Jaivik Bharat
 * are the national organic marks; the rest cover imports, food safety, and lab reports.
 */
public enum CertificationType {
    NPOP_INDIA_ORGANIC,
    JAIVIK_BHARAT,
    USDA_ORGANIC,
    EU_ORGANIC,
    FSSAI,
    LAB_REPORT,
    OTHER
}
