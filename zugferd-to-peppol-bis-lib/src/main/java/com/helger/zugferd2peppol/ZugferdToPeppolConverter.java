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

import java.io.IOException;
import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.cii.d16b.CIID16BCrossIndustryInvoiceTypeMarshaller;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.cii2ubl.EUBLCreationMode;
import com.helger.en16931.cii2ubl.en2017.CIID16BToUBL21Converter;
import com.helger.jaxb.validation.WrappedCollectingValidationEventHandler;
import com.helger.kaltblut.core.extract.HybridExtractor;
import com.helger.kaltblut.core.inspect.HybridInspector;
import com.helger.kaltblut.core.model.EZugferdFlavor;
import com.helger.kaltblut.core.model.EZugferdProfile;
import com.helger.kaltblut.core.model.HybridMetadata;
import com.helger.kaltblut.core.source.IHybridSource;
import com.helger.kaltblut.core.validate.HybridValidationResult;
import com.helger.kaltblut.core.validate.HybridValidator;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.ubl21.UBL21Marshaller;

import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.AttachmentType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.PartyType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.EmbeddedDocumentBinaryObjectType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.EndpointIDType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import un.unece.uncefact.data.standard.crossindustryinvoice._100.CrossIndustryInvoiceType;

/**
 * Converts a ZUGFeRD / Factur-X hybrid invoice (PDF/A-3 with embedded CII XML) to a Peppol BIS
 * Billing 3 UBL document. The original PDF is embedded as additional supporting document (BG-24),
 * so the XML is the one and only artefact to send.
 * <ol>
 * <li>Validate the PDF with kaltblut</li>
 * <li>Extract the embedded CII D16B XML</li>
 * <li>Convert it to UBL 2.1 with en16931-cii2ubl and force the Peppol BIS Billing 3 identifiers</li>
 * <li>Embed the PDF as base64 into the UBL</li>
 * <li>Validate the result with phive-rules-peppol</li>
 * </ol>
 *
 * @author Philip Helger
 */
public class ZugferdToPeppolConverter
{
  /** BT-24 of Peppol BIS Billing 3 */
  public static final String PEPPOL_BIS_BILLING_3_CUSTOMIZATION_ID = "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";
  /** BT-23 of Peppol BIS Billing 3 */
  public static final String PEPPOL_BIS_BILLING_3_PROFILE_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
  /**
   * MIME type of the embedded PDF.
   */
  public static final String PDF_MIME_CODE = "application/pdf";

  private static final Logger LOGGER = LoggerFactory.getLogger (ZugferdToPeppolConverter.class);

  private final ZugferdToPeppolSettings m_aSettings;

  /**
   * Constructor using default settings.
   */
  public ZugferdToPeppolConverter ()
  {
    this (new ZugferdToPeppolSettings ());
  }

  /**
   * Constructor.
   *
   * @param aSettings
   *        The settings to use. May not be <code>null</code>.
   */
  public ZugferdToPeppolConverter (@NonNull final ZugferdToPeppolSettings aSettings)
  {
    ValueEnforcer.notNull (aSettings, "Settings");
    m_aSettings = aSettings;
  }

  /**
   * @return The settings used by this converter. Never <code>null</code>.
   */
  @NonNull
  public final ZugferdToPeppolSettings getSettings ()
  {
    return m_aSettings;
  }

  private static String _getRejectionReason (final EZugferdFlavor eFlavor, final EZugferdProfile eProfile)
  {
    if (eFlavor == EZugferdFlavor.ZUGFERD_1)
      return "ZUGFeRD 1.0 uses a different CII syntax (CrossIndustryDocument) that cannot be converted to Peppol BIS Billing 3";
    if (eProfile == null)
      return "The ZUGFeRD / Factur-X profile could not be determined";
    if (eProfile == EZugferdProfile.MINIMUM || eProfile == EZugferdProfile.BASIC_WL)
      return "The profile '" +
             eProfile.getID () +
             "' contains no invoice lines and is not EN 16931 compliant, so it cannot be converted to Peppol BIS Billing 3";
    if (eProfile == EZugferdProfile.COMFORT)
      return "The profile 'COMFORT' is a ZUGFeRD 1.0 profile that cannot be converted to Peppol BIS Billing 3";
    return null;
  }

  private static void _applyEndpoint (@Nullable final PartyType aParty,
                                      @Nullable final String sScheme,
                                      @Nullable final String sID)
  {
    if (aParty != null && aParty.getEndpointID () == null && StringHelper.isNotEmpty (sID))
    {
      final EndpointIDType aEndpointID = new EndpointIDType ();
      aEndpointID.setValue (sID);
      aEndpointID.setSchemeID (sScheme);
      aParty.setEndpointID (aEndpointID);
    }
  }

  /**
   * Run the whole conversion.
   *
   * @param aSource
   *        The ZUGFeRD / Factur-X PDF. May not be <code>null</code>.
   * @return The result object. Never <code>null</code>.
   * @throws IOException
   *         If reading the PDF fails
   */
  @NonNull
  public ZugferdToPeppolResult convert (@NonNull final IHybridSource aSource) throws IOException
  {
    ValueEnforcer.notNull (aSource, "Source");

    // 1. Validate the hybrid invoice
    final HybridValidator aHybridValidator = new HybridValidator ();
    aHybridValidator.getSettings ()
                    .setCountry (m_aSettings.getCountry ())
                    .setCheckPdfA3 (m_aSettings.isCheckPdfA3 ())
                    .setApplyDePdfADowngrade (m_aSettings.isApplyDePdfADowngrade ());
    final HybridValidationResult aHybridResult = aHybridValidator.validate (aSource);
    final ErrorList aConversionErrors = new ErrorList ();
    if (!aHybridResult.isValid ())
      return new ZugferdToPeppolResult (aHybridResult, null, aConversionErrors, null, false, null);

    // 2. Check what we got
    final HybridMetadata aMetadata = HybridInspector.readMetadata (aSource);
    final String sRejectionReason = _getRejectionReason (aMetadata.getFlavor (), aMetadata.getProfile ());
    if (sRejectionReason != null)
      return new ZugferdToPeppolResult (aHybridResult, sRejectionReason, aConversionErrors, null, false, null);

    // 3. Extract and convert
    final byte [] aCII = HybridExtractor.extractInvoiceXml (aSource);
    final CrossIndustryInvoiceType aCIIInvoice = new CIID16BCrossIndustryInvoiceTypeMarshaller ().setValidationEventHandler (new WrappedCollectingValidationEventHandler (aConversionErrors))
                                                                                                 .read (aCII);
    if (aCIIInvoice == null)
      return new ZugferdToPeppolResult (aHybridResult,
                                        "The embedded XML is not a valid CII D16B document",
                                        aConversionErrors,
                                        null,
                                        false,
                                        null);

    final CIID16BToUBL21Converter aConverter = new CIID16BToUBL21Converter ();
    aConverter.setUBLCreationMode (EUBLCreationMode.AUTOMATIC)
              .setCustomizationID (PEPPOL_BIS_BILLING_3_CUSTOMIZATION_ID)
              .setProfileID (PEPPOL_BIS_BILLING_3_PROFILE_ID);
    final Serializable aUBL = aConverter.convertCIItoUBL (aCIIInvoice, aConversionErrors);
    if (aUBL == null)
      return new ZugferdToPeppolResult (aHybridResult,
                                        "The CII to UBL conversion failed",
                                        aConversionErrors,
                                        null,
                                        false,
                                        null);

    // 4. Embed the PDF
    final DocumentReferenceType aDocRef = new DocumentReferenceType ();
    aDocRef.setID (m_aSettings.getAttachmentFilename ());
    final EmbeddedDocumentBinaryObjectType aEmbedded = new EmbeddedDocumentBinaryObjectType ();
    aEmbedded.setMimeCode (PDF_MIME_CODE);
    aEmbedded.setFilename (m_aSettings.getAttachmentFilename ());
    aEmbedded.setValue (aSource.getBytes ());
    final AttachmentType aAttachment = new AttachmentType ();
    aAttachment.setEmbeddedDocumentBinaryObject (aEmbedded);
    aDocRef.setAttachment (aAttachment);

    final boolean bIsCreditNote;
    final byte [] aUBLXML;
    if (aUBL instanceof final InvoiceType aInvoice)
    {
      bIsCreditNote = false;
      aInvoice.addAdditionalDocumentReference (aDocRef);
      _applyEndpoint (aInvoice.getAccountingSupplierParty ().getParty (),
                      m_aSettings.getSellerEndpointScheme (),
                      m_aSettings.getSellerEndpointID ());
      _applyEndpoint (aInvoice.getAccountingCustomerParty ().getParty (),
                      m_aSettings.getBuyerEndpointScheme (),
                      m_aSettings.getBuyerEndpointID ());
      if (aInvoice.getBuyerReference () == null && aInvoice.getOrderReference () == null)
        aInvoice.setBuyerReference (m_aSettings.getDefaultBuyerReference ());
      aUBLXML = UBL21Marshaller.invoice ().setFormattedOutput (true).getAsBytes (aInvoice);
    }
    else
    {
      bIsCreditNote = true;
      final CreditNoteType aCreditNote = (CreditNoteType) aUBL;
      aCreditNote.addAdditionalDocumentReference (aDocRef);
      _applyEndpoint (aCreditNote.getAccountingSupplierParty ().getParty (),
                      m_aSettings.getSellerEndpointScheme (),
                      m_aSettings.getSellerEndpointID ());
      _applyEndpoint (aCreditNote.getAccountingCustomerParty ().getParty (),
                      m_aSettings.getBuyerEndpointScheme (),
                      m_aSettings.getBuyerEndpointID ());
      if (aCreditNote.getBuyerReference () == null && aCreditNote.getOrderReference () == null)
        aCreditNote.setBuyerReference (m_aSettings.getDefaultBuyerReference ());
      aUBLXML = UBL21Marshaller.creditNote ().setFormattedOutput (true).getAsBytes (aCreditNote);
    }
    if (aUBLXML == null)
      return new ZugferdToPeppolResult (aHybridResult,
                                        "Failed to serialize the UBL document",
                                        aConversionErrors,
                                        null,
                                        bIsCreditNote,
                                        null);

    // 5. Validate the result
    ValidationResultList aPeppolResult = null;
    if (m_aSettings.isValidatePeppol ())
      aPeppolResult = PeppolBisBillingValidator.validate (aUBLXML, bIsCreditNote);

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Created a UBL " + (bIsCreditNote ? "Credit Note" : "Invoice") + " of " + aUBLXML.length + " bytes");
    return new ZugferdToPeppolResult (aHybridResult, null, aConversionErrors, aUBLXML, bIsCreditNote, aPeppolResult);
  }
}
