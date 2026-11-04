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
package com.helger.zugferd2peppol.cli;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.diagnostics.error.IError;
import com.helger.io.file.FilenameHelper;
import com.helger.kaltblut.core.model.EZugferdCountry;
import com.helger.kaltblut.core.source.HybridSource;
import com.helger.kaltblut.core.validate.HybridFinding;
import com.helger.zugferd2peppol.ZugferdToPeppolConverter;
import com.helger.zugferd2peppol.ZugferdToPeppolResult;
import com.helger.zugferd2peppol.ZugferdToPeppolSettings;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line client to convert ZUGFeRD / Factur-X PDFs to Peppol BIS Billing 3 UBL.
 *
 * @author Philip Helger
 */
@Command (name = "zugferd-to-peppol-bis",
          mixinStandardHelpOptions = true,
          description = "Converts ZUGFeRD / Factur-X hybrid invoices to Peppol BIS Billing 3 UBL invoices with the PDF embedded",
          separator = " ")
public final class ZugferdToPeppolMain implements Callable <Integer>
{
  @Parameters (arity = "1..*", paramLabel = "PDF", description = "The ZUGFeRD / Factur-X PDF files to convert")
  private List <File> m_aFiles;

  @Option (names = { "-o", "--output-dir" },
           paramLabel = "dir",
           description = "Directory for the UBL files. Default is the directory of the respective PDF.")
  private File m_aOutputDir;

  @Option (names = { "-c", "--country" },
           description = "Country-specific carrier rules of kaltblut. Default is ${DEFAULT-VALUE}. Values: ${COMPLETION-CANDIDATES}")
  private EZugferdCountry m_eCountry = EZugferdCountry.OTHER;

  @Option (names = "--no-pdfa", description = "Skip the PDF/A-3 validation")
  private boolean m_bNoPdfA;

  @Option (names = "--no-de-pdfa-downgrade", description = "Do not downgrade PDF/A findings for German invoices")
  private boolean m_bNoDeDowngrade;

  @Option (names = "--seller-endpoint",
           paramLabel = "scheme:id",
           description = "Seller electronic address (BT-34), e.g. 0204:991-12345-67. Required by Peppol, not part of ZUGFeRD.")
  private String m_sSellerEndpoint;

  @Option (names = "--buyer-endpoint",
           paramLabel = "scheme:id",
           description = "Buyer electronic address (BT-49), e.g. 0204:991-76543-21. Required by Peppol, not part of ZUGFeRD.")
  private String m_sBuyerEndpoint;

  @Option (names = "--buyer-reference",
           paramLabel = "ref",
           description = "Buyer reference (BT-10) to use if the invoice has neither a buyer reference nor an order reference")
  private String m_sBuyerReference;

  @Option (names = "--no-peppol-validation", description = "Skip the validation of the result against Peppol BIS Billing 3")
  private boolean m_bNoPeppolValidation;

  @Option (names = "--force-output", description = "Write the UBL file even if the Peppol validation reports errors")
  private boolean m_bForceOutput;

  private static void _parseEndpoint (@Nullable final String sValue,
                                      final boolean bSeller,
                                      @NonNull final ZugferdToPeppolSettings aSettings)
  {
    if (sValue == null)
      return;
    final int nIdx = sValue.indexOf (':');
    if (nIdx <= 0 || nIdx == sValue.length () - 1)
      throw new IllegalArgumentException ("The endpoint '" + sValue + "' must have the format 'scheme:id'");
    final String sScheme = sValue.substring (0, nIdx);
    final String sID = sValue.substring (nIdx + 1);
    if (bSeller)
      aSettings.setSellerEndpoint (sScheme, sID);
    else
      aSettings.setBuyerEndpoint (sScheme, sID);
  }

  private boolean _convert (@NonNull final File aFile)
  {
    System.out.println ("File: " + aFile.getName ());
    try
    {
      final ZugferdToPeppolSettings aSettings = new ZugferdToPeppolSettings ();
      aSettings.setCountry (m_eCountry)
               .setCheckPdfA3 (!m_bNoPdfA)
               .setApplyDePdfADowngrade (!m_bNoDeDowngrade)
               .setValidatePeppol (!m_bNoPeppolValidation)
               .setAttachmentFilename (aFile.getName ())
               .setDefaultBuyerReference (m_sBuyerReference);
      _parseEndpoint (m_sSellerEndpoint, true, aSettings);
      _parseEndpoint (m_sBuyerEndpoint, false, aSettings);

      final ZugferdToPeppolResult aResult = new ZugferdToPeppolConverter (aSettings).convert (HybridSource.fromFile (aFile));

      for (final HybridFinding aFinding : aResult.getHybridResult ().getAllFindings ())
        System.out.println ("  [PDF] " + aFinding);
      if (aResult.getRejectionReason () != null)
        System.out.println ("  [REJECTED] " + aResult.getRejectionReason ());
      for (final IError aError : aResult.getConversionErrors ())
        System.out.println ("  [CONVERT] " + aError.getAsString (Locale.ROOT));
      if (aResult.getPeppolResult () != null)
        aResult.getPeppolResult ().forEachFlattened (x -> System.out.println ("  [PEPPOL] " + x.getAsString (Locale.ROOT)));

      if (aResult.hasUBLXML () && (aResult.isSuccess () || m_bForceOutput))
      {
        final File aDir = m_aOutputDir != null ? m_aOutputDir : aFile.getAbsoluteFile ().getParentFile ();
        final File aDestFile = new File (aDir, FilenameHelper.getBaseName (aFile.getName ()) + "-ubl.xml");
        Files.createDirectories (aDir.toPath ());
        Files.write (aDestFile.toPath (), aResult.getUBLXML ());
        System.out.println ("  Wrote " + aDestFile.getPath ());
      }
      System.out.println ("  => " + (aResult.isSuccess () ? "OK" : "FAILED"));
      return aResult.isSuccess ();
    }
    catch (final Exception ex)
    {
      System.err.println ("  Failed to convert '" + aFile.getPath () + "': " + ex.getMessage ());
      return false;
    }
  }

  /**
   * Convert all files.
   *
   * @return 0 if all files were converted successfully, 1 otherwise.
   */
  public Integer call ()
  {
    int nFailed = 0;
    for (final File aFile : m_aFiles)
      if (!_convert (aFile))
        nFailed++;
    return Integer.valueOf (nFailed == 0 ? 0 : 1);
  }

  /**
   * Main entry point.
   *
   * @param aArgs
   *        The command line arguments.
   */
  public static void main (final String [] aArgs)
  {
    final CommandLine aCmd = new CommandLine (new ZugferdToPeppolMain ());
    aCmd.setCaseInsensitiveEnumValuesAllowed (true);
    System.exit (aCmd.execute (aArgs));
  }
}
