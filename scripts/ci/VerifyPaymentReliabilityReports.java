import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Requires the payment lifecycle's PostgreSQL suites to execute successfully in CI. */
class VerifyPaymentReliabilityReports {
    public static void main(String[] args) throws Exception {
        Path root = (args.length == 0 ? Path.of("") : Path.of(args[0])).toAbsolutePath();
        var suites = new LinkedHashMap<String, List<String>>();
        suites.put("payment-service", List.of(
                "com.ecommerce.payment.service.impl.CheckoutSessionPostgresTest",
                "com.ecommerce.payment.service.impl.PaymentConfirmationPostgresTest",
                "com.ecommerce.payment.service.impl.PaymentRefundPostgresTest"));
        suites.put("order-service", List.of(
                "com.ecommerce.order.repository.OrderPaymentExpiryPostgresTest"));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var parser = factory.newDocumentBuilder();
        boolean failed = false;
        for (var module : suites.entrySet()) {
            for (String suite : module.getValue()) {
                Path report = root.resolve(module.getKey()).resolve("target/surefire-reports")
                        .resolve("TEST-" + suite + ".xml");
                try {
                    var result = parser.parse(report.toFile()).getDocumentElement();
                    if (!"testsuite".equals(result.getTagName()) || !suite.equals(result.getAttribute("name"))) {
                        throw new IllegalStateException("unexpected report suite");
                    }
                    int tests = Integer.parseInt(result.getAttribute("tests"));
                    if (tests < 1) throw new IllegalStateException("suite did not run any tests");
                    for (String status : List.of("failures", "errors", "skipped")) {
                        if (Integer.parseInt(result.getAttribute(status)) != 0) {
                            throw new IllegalStateException("suite contains " + status);
                        }
                    }
                    System.out.println("PASS " + suite + ": " + tests + " tests, no failures or skips");
                } catch (Exception exception) {
                    System.err.println("ERROR " + report + ": " + exception.getMessage());
                    failed = true;
                }
            }
        }
        if (failed) System.exit(1);
    }
}
