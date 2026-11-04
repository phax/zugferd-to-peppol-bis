package com.helger.zugferd2peppol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

import com.helger.kaltblut.core.source.HybridSource;
import com.helger.kaltblut.testfiles.KaltblutTestFiles;

/**
 * Test class for class {@link ZugferdToPeppolConverter}.
 *
 * @author Philip Helger
 */
public final class ZugferdToPeppolConverterTest
{
  private static ZugferdToPeppolResult _convert (final String sPath) throws IOException
  {
    final byte [] aPDF = KaltblutTestFiles.readBytes (sPath);
    assertNotNull (sPath, aPDF);
    final ZugferdToPeppolSettings aSettings = new ZugferdToPeppolSettings ().setSellerEndpoint ("0204", "991-12345-67")
                                                                           .setBuyerEndpoint ("0204", "991-76543-21")
                                                                           .setDefaultBuyerReference ("04011000-12345-34");
    final ZugferdToPeppolResult aResult = new ZugferdToPeppolConverter (aSettings).convert (HybridSource.fromBytes (aPDF));
    if (aResult.getPeppolResult () != null)
      aResult.getPeppolResult ().forEachFlattened (x -> System.out.println (sPath + ": " + x.getAsString (java.util.Locale.ROOT)));
    return aResult;
  }

  private static void _assertSuccess (final String sPath, final String... aAllowedErrorIDs) throws IOException
  {
    final ZugferdToPeppolResult aResult = _convert (sPath);
    assertNull (aResult.getRejectionReason ());
    assertTrue (aResult.getHybridResult ().toString (), aResult.getHybridResult ().isValid ());
    assertTrue (aResult.getConversionErrors ().toString (), aResult.getConversionErrors ().containsNoError ());
    assertTrue (aResult.hasUBLXML ());
    if (aAllowedErrorIDs.length == 0)
      assertTrue (aResult.isSuccess ());
    else
    {
      // Only errors that are caused by missing data in the source sample
      assertFalse (aResult.isSuccess ());
      aResult.getPeppolResult ().forEachFlattened (x -> {
        if (x.getErrorLevel ().isError ())
          assertTrue (x.getErrorID (), Arrays.asList (aAllowedErrorIDs).contains (x.getErrorID ()));
      });
    }

    final String sUBL = new String (aResult.getUBLXML (), StandardCharsets.UTF_8);
    assertTrue (sUBL.contains (ZugferdToPeppolConverter.PEPPOL_BIS_BILLING_3_CUSTOMIZATION_ID));
    assertTrue (sUBL.contains (ZugferdToPeppolConverter.PEPPOL_BIS_BILLING_3_PROFILE_ID));
    assertTrue (sUBL.contains ("EmbeddedDocumentBinaryObject"));
  }

  @Test
  public void testEN16931 () throws IOException
  {
    // The sample lacks payment means and seller contact, which the German rules in Peppol demand
    _assertSuccess (KaltblutTestFiles.ZF_2_0_1_EN16931, "DE-R-001", "DE-R-002");
    // The sample uses the invoice type code 204 which is not allowed in Peppol
    _assertSuccess (KaltblutTestFiles.ZF_2_1_EN16931_EMBEDDED, "PEPPOL-EN16931-P0100");
  }

  @Test
  public void testXRechnung () throws IOException
  {
    _assertSuccess (KaltblutTestFiles.ZF_2_2_XRECHNUNG);
  }

  @Test
  public void testRejectedProfiles () throws IOException
  {
    for (final String s : Arrays.asList (KaltblutTestFiles.ZF_2_3_MINIMUM, KaltblutTestFiles.ZF_2_4_BASIC_WL))
    {
      final ZugferdToPeppolResult aResult = _convert (s);
      assertNotNull (s, aResult.getRejectionReason ());
      assertFalse (aResult.hasUBLXML ());
      assertFalse (aResult.isSuccess ());
    }
  }
}
