/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.zugferd2peppol;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.kaltblut.core.model.EZugferdCountry;

/**
 * Settings for {@link ZugferdToPeppolConverter}.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class ZugferdToPeppolSettings
{
  /**
   * Default value for {@link #isCheckPdfA3()}.
   */
  public static final boolean DEFAULT_CHECK_PDFA3 = true;
  /**
   * Default value for {@link #isApplyDePdfADowngrade()}.
   */
  public static final boolean DEFAULT_APPLY_DE_PDFA_DOWNGRADE = true;
  /**
   * Default value for {@link #isValidatePeppol()}.
   */
  public static final boolean DEFAULT_VALIDATE_PEPPOL = true;
  /**
   * Default value for {@link #getAttachmentFilename()}.
   */
  public static final String DEFAULT_ATTACHMENT_FILENAME = "invoice.pdf";

  private EZugferdCountry m_eCountry = EZugferdCountry.OTHER;
  private boolean m_bCheckPdfA3 = DEFAULT_CHECK_PDFA3;
  private boolean m_bApplyDePdfADowngrade = DEFAULT_APPLY_DE_PDFA_DOWNGRADE;
  private boolean m_bValidatePeppol = DEFAULT_VALIDATE_PEPPOL;
  private String m_sAttachmentFilename = DEFAULT_ATTACHMENT_FILENAME;
  private String m_sSellerEndpointScheme;
  private String m_sSellerEndpointID;
  private String m_sBuyerEndpointScheme;
  private String m_sBuyerEndpointID;
  private String m_sDefaultBuyerReference;

  /**
   * Constructor using all default values.
   */
  public ZugferdToPeppolSettings ()
  {}

  /**
   * @return The country for the country-specific kaltblut carrier rules. Never <code>null</code>.
   */
  @NonNull
  public EZugferdCountry getCountry ()
  {
    return m_eCountry;
  }

  /**
   * Set the country for the country-specific kaltblut carrier rules.
   *
   * @param eCountry
   *        The country. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setCountry (@NonNull final EZugferdCountry eCountry)
  {
    ValueEnforcer.notNull (eCountry, "Country");
    m_eCountry = eCountry;
    return this;
  }

  /**
   * @return <code>true</code> if the PDF/A-3 conformance should be checked.
   */
  public boolean isCheckPdfA3 ()
  {
    return m_bCheckPdfA3;
  }

  /**
   * Enable or disable the PDF/A-3 check.
   *
   * @param bCheckPdfA3
   *        <code>true</code> to check PDF/A-3.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setCheckPdfA3 (final boolean bCheckPdfA3)
  {
    m_bCheckPdfA3 = bCheckPdfA3;
    return this;
  }

  /**
   * @return <code>true</code> if PDF/A findings for German invoices are downgraded.
   */
  public boolean isApplyDePdfADowngrade ()
  {
    return m_bApplyDePdfADowngrade;
  }

  /**
   * Enable or disable the downgrade of PDF/A findings for German invoices.
   *
   * @param bApplyDePdfADowngrade
   *        <code>true</code> to downgrade.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setApplyDePdfADowngrade (final boolean bApplyDePdfADowngrade)
  {
    m_bApplyDePdfADowngrade = bApplyDePdfADowngrade;
    return this;
  }

  /**
   * @return <code>true</code> if the result is validated against Peppol BIS Billing 3.
   */
  public boolean isValidatePeppol ()
  {
    return m_bValidatePeppol;
  }

  /**
   * Enable or disable the Peppol BIS Billing 3 validation of the result.
   *
   * @param bValidatePeppol
   *        <code>true</code> to validate.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setValidatePeppol (final boolean bValidatePeppol)
  {
    m_bValidatePeppol = bValidatePeppol;
    return this;
  }

  /**
   * @return The file name used for the embedded PDF (<code>@filename</code> of BT-125). Never
   *         empty.
   */
  @NonNull
  public String getAttachmentFilename ()
  {
    return m_sAttachmentFilename;
  }

  /**
   * Set the file name of the embedded PDF. It is also used as the reference ID.
   *
   * @param sAttachmentFilename
   *        The file name. May neither be <code>null</code> nor empty.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setAttachmentFilename (@NonNull final String sAttachmentFilename)
  {
    ValueEnforcer.notEmpty (sAttachmentFilename, "AttachmentFilename");
    m_sAttachmentFilename = sAttachmentFilename;
    return this;
  }

  /**
   * @return The seller endpoint scheme or <code>null</code>.
   */
  @Nullable
  public String getSellerEndpointScheme ()
  {
    return m_sSellerEndpointScheme;
  }

  /**
   * @return The seller endpoint ID or <code>null</code>.
   */
  @Nullable
  public String getSellerEndpointID ()
  {
    return m_sSellerEndpointID;
  }

  /**
   * Set the seller electronic address (BT-34). ZUGFeRD has no equivalent, but Peppol requires it.
   * It is only applied if the UBL has no seller endpoint ID.
   *
   * @param sScheme
   *        The Peppol participant identifier scheme (e.g. <code>0204</code>). May be
   *        <code>null</code>.
   * @param sID
   *        The endpoint ID. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setSellerEndpoint (@Nullable final String sScheme, @Nullable final String sID)
  {
    m_sSellerEndpointScheme = sScheme;
    m_sSellerEndpointID = sID;
    return this;
  }

  /**
   * @return The buyer endpoint scheme or <code>null</code>.
   */
  @Nullable
  public String getBuyerEndpointScheme ()
  {
    return m_sBuyerEndpointScheme;
  }

  /**
   * @return The buyer endpoint ID or <code>null</code>.
   */
  @Nullable
  public String getBuyerEndpointID ()
  {
    return m_sBuyerEndpointID;
  }

  /**
   * Set the buyer electronic address (BT-49). ZUGFeRD has no equivalent, but Peppol requires it. It
   * is only applied if the UBL has no buyer endpoint ID.
   *
   * @param sScheme
   *        The Peppol participant identifier scheme (e.g. <code>0204</code>). May be
   *        <code>null</code>.
   * @param sID
   *        The endpoint ID. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setBuyerEndpoint (@Nullable final String sScheme, @Nullable final String sID)
  {
    m_sBuyerEndpointScheme = sScheme;
    m_sBuyerEndpointID = sID;
    return this;
  }

  /**
   * @return The default buyer reference or <code>null</code>.
   */
  @Nullable
  public String getDefaultBuyerReference ()
  {
    return m_sDefaultBuyerReference;
  }

  /**
   * Set the buyer reference (BT-10) to use if the invoice has neither a buyer reference nor a
   * purchase order reference (BT-13). Peppol requires one of them.
   *
   * @param sDefaultBuyerReference
   *        The default value. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public ZugferdToPeppolSettings setDefaultBuyerReference (@Nullable final String sDefaultBuyerReference)
  {
    m_sDefaultBuyerReference = sDefaultBuyerReference;
    return this;
  }
}
