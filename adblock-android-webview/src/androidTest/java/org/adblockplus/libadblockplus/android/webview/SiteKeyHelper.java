/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.libadblockplus.android.webview;

import android.util.Pair;

import org.adblockplus.libadblockplus.android.AndroidBase64Processor;
import org.adblockplus.libadblockplus.security.JavaSignatureVerifier;
import org.adblockplus.libadblockplus.security.SignatureVerifier;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolder;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeyException;
import org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier;
import org.adblockplus.libadblockplus.util.Base64Exception;
import org.adblockplus.libadblockplus.util.Base64Processor;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;

public class SiteKeyHelper
{
  public final SignatureVerifier signatureVerifier = new JavaSignatureVerifier();
  public final PublicKeyHolder publicKeyHolder = new PublicKeyHolderImpl();
  public final Base64Processor base64Processor = new AndroidBase64Processor();
  public final TestSiteKeyVerifier siteKeyVerifier =
      new TestSiteKeyVerifier(signatureVerifier, publicKeyHolder, base64Processor);

  public static class TestSiteKeyVerifier extends SiteKeyVerifier
  {
    public TestSiteKeyVerifier(final SignatureVerifier signatureVerifier,
                               final PublicKeyHolder publicKeyHolder,
                               final Base64Processor base64Processor)
    {
      super(signatureVerifier, publicKeyHolder, base64Processor);
    }

    @Override
    // exposed method as `public`
    public byte[] buildData(final String url, final String userAgent) throws SiteKeyException
    {
      return super.buildData(url, userAgent);
    }
  }

  // returns (publicKey /* with base64 padding in the end */, siteKey)
  public Pair<String, String> buildXAdblockKeyValue(final String url, final String userAgent)
      throws SiteKeyException, Base64Exception,
      NoSuchAlgorithmException, InvalidKeyException, SignatureException
  {
    final byte[] data = siteKeyVerifier.buildData(url, userAgent);
    final int KEY_LENGTH_BITS = 512;

    // generate key pair
    final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(JavaSignatureVerifier.KEY_ALGORITHM);
    final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
    keyGen.initialize(KEY_LENGTH_BITS, random);
    final KeyPair keyPair = keyGen.generateKeyPair();

    // sign and encode to Base64
    final Signature signature = Signature.getInstance(JavaSignatureVerifier.SIGNATURE_ALGORITHM);
    signature.initSign(keyPair.getPrivate());
    signature.update(data);
    final byte[] signatureBytes = signature.sign();
    final String encodedSignature = base64Processor.encodeToString(signatureBytes);

    // encode public key to Base64
    final byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
    final String encodedPublicKey = base64Processor.encodeToString(publicKeyBytes).trim();
    return new Pair<>(encodedPublicKey, encodedPublicKey + "_" + encodedSignature);
  }
}
