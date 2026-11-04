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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phive.api.execute.ValidationExecutionManager;
import com.helger.phive.api.executorset.IValidationExecutorSet;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.validity.IValidityDeterminator;
import com.helger.phive.peppol.PeppolValidation2025_11;
import com.helger.phive.peppol.PeppolValidation2026_05;
import com.helger.phive.peppol.PeppolValidationBisEurope;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Validates a UBL Invoice or Credit Note against the currently active Peppol BIS Billing 3
 * rules (via phive-rules-peppol).
 *
 * @author Philip Helger
 */
@Immutable
public final class PeppolBisBillingValidator
{
  private static final Logger LOGGER = LoggerFactory.getLogger (PeppolBisBillingValidator.class);

  private static final ValidationExecutorSetRegistry <IValidationSourceXML> REGISTRY = new ValidationExecutorSetRegistry <> ();
  static
  {
    PeppolValidationBisEurope.initBilling (REGISTRY);
  }

  private PeppolBisBillingValidator ()
  {}

  /**
   * Validate the provided UBL XML.
   *
   * @param aUBLXML
   *        The UBL XML bytes. May not be <code>null</code>.
   * @param bIsCreditNote
   *        <code>true</code> for a UBL Credit Note, <code>false</code> for a UBL Invoice.
   * @return The validation result list. Never <code>null</code>.
   */
  @NonNull
  public static ValidationResultList validate (final byte @NonNull [] aUBLXML, final boolean bIsCreditNote)
  {
    ValueEnforcer.notNull (aUBLXML, "UBLXML");

    final boolean bLatest = PeppolValidationBisEurope.getVersionToUse ().equals (PeppolValidation2026_05.VERSION_STR);
    final IValidationExecutorSet <IValidationSourceXML> aVES = REGISTRY.getOfID (bIsCreditNote ? (bLatest ? PeppolValidation2026_05.VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3
                                                                                                          : PeppolValidation2025_11.VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3)
                                                                                               : (bLatest ? PeppolValidation2026_05.VID_OPENPEPPOL_INVOICE_UBL_V3
                                                                                                          : PeppolValidation2025_11.VID_OPENPEPPOL_INVOICE_UBL_V3));
    if (aVES == null)
      throw new IllegalStateException ("Failed to resolve the Peppol BIS Billing validation execution set");
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Validating against " + aVES.getDisplayName ());

    final Document aDoc = DOMReader.readXMLDOM (aUBLXML);
    if (aDoc == null)
      throw new IllegalArgumentException ("The provided UBL XML is not well-formed");

    return ValidationExecutionManager.executeValidation (IValidityDeterminator.createDefault (),
                                                         aVES,
                                                         ValidationSourceXML.create ("ubl.xml", aDoc));
  }
}
