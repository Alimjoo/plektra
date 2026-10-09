import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import jdk.security.jarsigner.JarSigner;

/** JDK JAR signing with the RSA operation delegated to the local PIV helper. */
public final class SignPlayBundle {
    static List<String> command;

    public static final class PivKey implements PrivateKey {
        public String getAlgorithm() { return "RSA"; }
        public String getFormat() { return null; }
        public byte[] getEncoded() { return null; }
    }

    public static final class PivSignature extends SignatureSpi {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        protected void engineInitVerify(PublicKey key) throws InvalidKeyException {
            throw new InvalidKeyException("Use a standard RSA provider to verify");
        }
        protected void engineInitSign(PrivateKey key) throws InvalidKeyException {
            if (!(key instanceof PivKey)) throw new InvalidKeyException("Expected PIV key");
            bytes.reset();
        }
        protected void engineUpdate(byte b) { bytes.write(b); }
        protected void engineUpdate(byte[] b, int off, int len) { bytes.write(b, off, len); }
        protected byte[] engineSign() throws SignatureException {
            try {
                var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                try (var stdin = process.getOutputStream()) { bytes.writeTo(stdin); }
                byte[] signature = process.getInputStream().readAllBytes();
                if (process.waitFor() != 0 || signature.length != 512)
                    throw new SignatureException("PIV signing failed");
                return signature;
            } catch (IOException | InterruptedException e) {
                throw new SignatureException(e);
            }
        }
        protected boolean engineVerify(byte[] signature) { throw new UnsupportedOperationException(); }
        @Deprecated protected void engineSetParameter(String p, Object v) { throw new UnsupportedOperationException(); }
        @Deprecated protected Object engineGetParameter(String p) { throw new UnsupportedOperationException(); }
    }

    public static final class PivProvider extends Provider {
        public PivProvider() {
            super("PlektraPIV", "1.0", "Yubico PIV RSA signing bridge");
            put("Signature.SHA256withRSA", PivSignature.class.getName());
        }
    }

    static X509Certificate certificate(String path) throws Exception {
        try (var input = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("verify")) {
            var expected = certificate(args[2]);
            int count = 0;
            try (var jar = new JarFile(args[1], true)) {
                for (var entry : Collections.list(jar.entries())) {
                    if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                    try (var stream = jar.getInputStream(entry)) { stream.transferTo(OutputStream.nullOutputStream()); }
                    var certs = entry.getCertificates();
                    if (certs == null || certs.length != 1 || !certs[0].equals(expected))
                        throw new SecurityException("Unexpected or missing signer: " + entry.getName());
                    count++;
                }
            }
            if (count == 0) throw new SecurityException("Empty bundle");
            System.out.println("Verified " + count + " bundle entries against the expected upload certificate.");
            return;
        }
        var cert = certificate(args[3]);
        cert.checkValidity();
        var certPath = CertificateFactory.getInstance("X.509").generateCertPath(List.of(cert));
        command = List.of(args[4], args[5], "--rsa-sign", "--channel", args[6]);
        var signer = new JarSigner.Builder(new PivKey(), certPath)
            .digestAlgorithm("SHA-256").signatureAlgorithm("SHA256withRSA", new PivProvider())
            .signerName("PLEKTRA").build();
        try (var zip = new ZipFile(args[1]);
             var output = Files.newOutputStream(Path.of(args[2]), StandardOpenOption.CREATE_NEW)) {
            signer.sign(zip, output);
        }
    }
}
