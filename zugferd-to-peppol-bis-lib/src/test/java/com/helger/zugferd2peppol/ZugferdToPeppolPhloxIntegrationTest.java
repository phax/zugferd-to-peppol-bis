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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.font.roboto.EFontResourceRoboto;
import com.helger.io.resource.ClassPathResource;
import com.helger.kaltblut.core.source.HybridSource;
import com.helger.pdflayout.pdfa.EPdfALevel;
import com.helger.phlox.api.model.PhloxInvoice;
import com.helger.phlox.cii.CiiInvoiceReader;
import com.helger.phlox.pdf.PdfEmbeddedSource;
import com.helger.phlox.pdf.PdfFontFamily;
import com.helger.phlox.pdf.PdfInvoiceWriter;
import com.helger.phlox.pdf.PdfRenderContext;
import com.helger.phlox.testfiles.MockPhloxTestFiles;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Integration test for class {@link ZugferdToPeppolConverter}: the ZUGFeRD / Factur-X invoices are
 * not taken from a collection of samples but are created on the fly with phlox
 * (<code>../phlox</code>) from CII D16B invoices. The test makes sure, that what phlox writes is
 * accepted by the converter and that the result is a valid Peppol BIS Billing 3 invoice.
 *
 * @author Philip Helger
 */
public final class ZugferdToPeppolPhloxIntegrationTest
{
  private static final String UBL_INVOICE_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String UBL_CREDITNOTE_NS = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
  private static final String CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

  private static final String CII_INVOICE = "2017/cii/CII_example7.xml";
  private static final String CII_CREDITNOTE = "2017/cii/CII_example1-CreditNote.xml";

  private static byte @NonNull [] _readBytes (@NonNull final String sPath)
  {
    final byte [] ret = StreamHelper.getAllBytes (new ClassPathResource (MockPhloxTestFiles.BASE_PATH + sPath)
                                                                                                              .getInputStream ());
    assertNotNull (sPath, ret);
    return ret;
  }

  /**
   * Create a ZUGFeRD / Factur-X hybrid invoice (PDF/A-3b with the embedded CII) with phlox.
   */
  private static byte @NonNull [] _createZugferd (@NonNull final String sPath, final byte @NonNull [] aCII)
                                                                                                            throws Exception
  {
    final ErrorList aErrors = new ErrorList ();
    final PhloxInvoice aInvoice = CiiInvoiceReader.fromD16B ()
                                                  .read (new NonBlockingByteArrayInputStream (aCII), aErrors);
    assertNotNull (sPath + ": " + aErrors, aInvoice);

    final PdfRenderContext aCtx = new PdfRenderContext ().setFontFamily (PdfFontFamily.of (EFontResourceRoboto.ROBOTO_NORMAL,
                                                                                           EFontResourceRoboto.ROBOTO_BOLD,
                                                                                           EFontResourceRoboto.ROBOTO_NORMAL_ITALIC,
                                                                                           EFontResourceRoboto.ROBOTO_BOLD_ITALIC))
                                                         .setPdfALevel (EPdfALevel.PDF_A_3B)
                                                         .setDocumentCreationDateTime (ZonedDateTime.of (2026,
                                                                                                         10,
                                                                                                         28,
                                                                                                         12,
                                                                                                         0,
                                                                                                         0,
                                                                                                         0,
                                                                                                         ZoneOffset.UTC))
                                                         .setEmbeddedSource (PdfEmbeddedSource.ofFacturX (aCII));
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    new PdfInvoiceWriter ().writeTo (aInvoice, aCtx, aBAOS);
    return aBAOS.toByteArray ();
  }

  @NonNull
  private static ZugferdToPeppolResult _convert (final byte @NonNull [] aPDF) throws Exception
  {
    final ZugferdToPeppolSettings aSettings = new ZugferdToPeppolSettings ().setSellerEndpoint ("0204", "991-12345-67")
                                                                            .setBuyerEndpoint ("0204", "991-76543-21")
                                                                            .setDefaultBuyerReference ("04011000-12345-34");
    return new ZugferdToPeppolConverter (aSettings).convert (HybridSource.fromBytes (aPDF));
  }

  @NonNull
  private static String _getFirstText (@NonNull final Document aDoc,
                                       @NonNull final String sNS,
                                       @NonNull final String sLocalName)
  {
    final NodeList aNL = aDoc.getElementsByTagNameNS (sNS, sLocalName);
    assertTrue (sLocalName, aNL.getLength () > 0);
    return aNL.item (0).getTextContent ().trim ();
  }

  private static void _testConvert (@NonNull final String sPath,
                                    final boolean bIsCreditNote,
                                    final String... aAllowedErrorIDs) throws Exception
  {
    final byte [] aCII = _readBytes (sPath);
    final byte [] aPDF = _createZugferd (sPath, aCII);

    final ZugferdToPeppolResult aResult = _convert (aPDF);
    if (aResult.getPeppolResult () != null)
      aResult.getPeppolResult ()
             .forEachFlattened (x -> System.out.println (sPath + ": " + x.getAsString (Locale.ROOT)));

    // What phlox created is a valid hybrid invoice
    assertNull (aResult.getRejectionReason ());
    assertTrue (aResult.getHybridResult ().getAllFindings ().toString (), aResult.getHybridResult ().isValid ());
    assertTrue (aResult.getConversionErrors ().toString (), aResult.getConversionErrors ().containsNoError ());
    assertTrue (aResult.hasUBLXML ());
    // ... that converts to a valid Peppol BIS Billing 3 invoice
    if (aAllowedErrorIDs.length == 0)
      assertTrue (aResult.isSuccess ());
    else
    {
      // Only errors that are caused by the data of the source sample
      assertFalse (aResult.isSuccess ());
      aResult.getPeppolResult ().forEachFlattened (x -> {
        if (x.getErrorLevel ().isError ())
          assertTrue (x.getErrorID (), Arrays.asList (aAllowedErrorIDs).contains (x.getErrorID ()));
      });
    }

    final Document aDoc = DOMReader.readXMLDOM (new String (aResult.getUBLXML (), StandardCharsets.UTF_8));
    assertNotNull (aDoc);
    final Element aRoot = aDoc.getDocumentElement ();
    assertEquals (bIsCreditNote ? UBL_CREDITNOTE_NS : UBL_INVOICE_NS, aRoot.getNamespaceURI ());
    assertEquals (ZugferdToPeppolConverter.PEPPOL_BIS_BILLING_3_CUSTOMIZATION_ID,
                  _getFirstText (aDoc, CBC_NS, "CustomizationID"));
    assertEquals (ZugferdToPeppolConverter.PEPPOL_BIS_BILLING_3_PROFILE_ID, _getFirstText (aDoc, CBC_NS, "ProfileID"));

    // The ID of the CII must be the ID of the UBL
    final Document aCIIDoc = DOMReader.readXMLDOM (new String (aCII, StandardCharsets.UTF_8));
    assertNotNull (aCIIDoc);
    final Element aCIIExchangedDocument = (Element) aCIIDoc.getElementsByTagNameNS ("*", "ExchangedDocument").item (0);
    final String sCIIID = aCIIExchangedDocument.getElementsByTagNameNS ("*", "ID").item (0).getTextContent ().trim ();
    assertEquals (sCIIID, aRoot.getElementsByTagNameNS (CBC_NS, "ID").item (0).getTextContent ().trim ());

    // The PDF is embedded
    final NodeList aEmbedded = aDoc.getElementsByTagNameNS (CBC_NS, "EmbeddedDocumentBinaryObject");
    assertEquals (1, aEmbedded.getLength ());
    assertEquals ("application/pdf", ((Element) aEmbedded.item (0)).getAttribute ("mimeCode"));
  }

  @Test
  public void testInvoice () throws Exception
  {
    _testConvert (CII_INVOICE, false);
  }

  @Test
  public void testCreditNote () throws Exception
  {
    // The sample violates the Dutch rules for the invoice type code and the seller
    _testConvert (CII_CREDITNOTE, true, "NL-R-001", "NL-R-003");
  }
}
