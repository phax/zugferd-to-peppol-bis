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

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.kaltblut.core.validate.HybridValidationResult;
import com.helger.phive.api.result.ValidationResultList;

/**
 * The result of {@link ZugferdToPeppolConverter#convert(com.helger.kaltblut.core.source.IHybridSource)}.
 *
 * @author Philip Helger
 */
@Immutable
public final class ZugferdToPeppolResult
{
  private final HybridValidationResult m_aHybridResult;
  private final String m_sRejectionReason;
  private final ErrorList m_aConversionErrors;
  private final byte [] m_aUBLXML;
  private final boolean m_bCreditNote;
  private final ValidationResultList m_aPeppolResult;

  /**
   * Constructor.
   *
   * @param aHybridResult
   *        The kaltblut validation result. May not be <code>null</code>.
   * @param sRejectionReason
   *        The reason why the document was rejected. May be <code>null</code>.
   * @param aConversionErrors
   *        The conversion errors. May not be <code>null</code>.
   * @param aUBLXML
   *        The created UBL XML. May be <code>null</code>.
   * @param bCreditNote
   *        <code>true</code> if the UBL is a Credit Note.
   * @param aPeppolResult
   *        The Peppol validation result. May be <code>null</code>.
   */
  public ZugferdToPeppolResult (@NonNull final HybridValidationResult aHybridResult,
                                @Nullable final String sRejectionReason,
                                @NonNull final ErrorList aConversionErrors,
                                final byte @Nullable [] aUBLXML,
                                final boolean bCreditNote,
                                @Nullable final ValidationResultList aPeppolResult)
  {
    ValueEnforcer.notNull (aHybridResult, "HybridResult");
    ValueEnforcer.notNull (aConversionErrors, "ConversionErrors");
    m_aHybridResult = aHybridResult;
    m_sRejectionReason = sRejectionReason;
    m_aConversionErrors = aConversionErrors;
    m_aUBLXML = aUBLXML;
    m_bCreditNote = bCreditNote;
    m_aPeppolResult = aPeppolResult;
  }

  /**
   * @return The kaltblut validation result of the incoming PDF. Never <code>null</code>.
   */
  @NonNull
  public HybridValidationResult getHybridResult ()
  {
    return m_aHybridResult;
  }

  /**
   * @return The reason why the document cannot be converted at all (e.g. unsupported profile) or
   *         <code>null</code> if it was not rejected.
   */
  @Nullable
  public String getRejectionReason ()
  {
    return m_sRejectionReason;
  }

  /**
   * @return The errors and warnings of the CII to UBL conversion. Never <code>null</code>.
   */
  @NonNull
  public ErrorList getConversionErrors ()
  {
    return m_aConversionErrors;
  }

  /**
   * @return The created UBL XML or <code>null</code> if none was created. If not
   *         <code>null</code> this may still be invalid - check {@link #isSuccess()}.
   */
  public byte @Nullable [] getUBLXML ()
  {
    return m_aUBLXML;
  }

  /**
   * @return <code>true</code> if a UBL XML was created.
   */
  public boolean hasUBLXML ()
  {
    return m_aUBLXML != null;
  }

  /**
   * @return <code>true</code> if the created UBL is a Credit Note, <code>false</code> if it is an
   *         Invoice. Only meaningful if {@link #hasUBLXML()}.
   */
  public boolean isCreditNote ()
  {
    return m_bCreditNote;
  }

  /**
   * @return The Peppol BIS Billing validation result or <code>null</code> if no UBL was created or
   *         Peppol validation was disabled.
   */
  @Nullable
  public ValidationResultList getPeppolResult ()
  {
    return m_aPeppolResult;
  }

  /**
   * @return <code>true</code> if the incoming PDF is valid, the conversion worked without errors
   *         and (if enabled) the result is valid according to the Peppol BIS Billing rules.
   */
  public boolean isSuccess ()
  {
    return m_sRejectionReason == null &&
           m_aHybridResult.isValid () &&
           m_aConversionErrors.containsNoError () &&
           m_aUBLXML != null &&
           (m_aPeppolResult == null || m_aPeppolResult.containsNoError ());
  }
}
