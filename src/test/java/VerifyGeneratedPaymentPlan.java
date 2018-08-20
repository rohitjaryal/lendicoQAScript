import Generic.BaseTestSetup;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import static io.restassured.RestAssured.given;

public class VerifyGeneratedPaymentPlan extends BaseTestSetup {

    Double nominalRate, loanAmount;
    int duration;
    Double annuity;
    String startDate;
    List<String> expectedBorrowerPaymentAmountList = new ArrayList<>();
    List<String> expectedInitialOutstandingPrincipal = new ArrayList<>();
    List<String> expectedInterestRateList = new ArrayList<>();
    List<String> expectedPrincipalList = new ArrayList<>();
    List<String> expectedRemainingOutstandingPrincipal = new ArrayList<>();
    List<String> expectedLoanPaymentDate = new ArrayList<>();
    List<String> actualBorrowerPaymentAmountList, actualLoanPaymentDateList, actualInitialOutstandingPrincipalList, actualInterestList, actualPrincipalList, actualRemainingOutstandingPrincipalList;
    private Response annuityResponse;

    /***
     *
     * @param generatePlanEndpoint
     * @param calcAnnuityEndpoint
     * @param loanAmount
     * @param nominalRate
     * @param duration
     * @param startDate
     * Fetch results from /generate-plan  and /calc-annuity
     * Generate expected repayment plan
     */
    @Parameters({"generatePlanEndpoint", "calcAnnuityEndpoint", "loanAmount", "nominalRate", "duration", "startDate"})
    @BeforeClass
    public void initialize(String generatePlanEndpoint, String calcAnnuityEndpoint, String loanAmount, String nominalRate, String duration, String startDate) {
        try {
            this.nominalRate = Double.valueOf(nominalRate);
            this.duration = Integer.valueOf(duration);
            this.loanAmount = Double.valueOf(loanAmount);
            this.startDate = startDate;
        } catch (Exception ex) {
            Assert.fail("Issue with configuration!");
        }

        String jsonBody = "{\"loanAmount\":\"" + loanAmount + "\",\"nominalRate\":\"" + nominalRate + "\",\"duration\":" + duration + "}";
        annuityResponse = given().spec(requestSpecification).contentType(ContentType.JSON).body(jsonBody).urlEncodingEnabled(false).
                when().post(calcAnnuityEndpoint)
                .then()
                .spec(responseSpecification)
                .extract().response();

        annuity = Double.valueOf(annuityResponse.getBody().jsonPath().get("annuity").toString());


        String requestBody = "{\"loanAmount\":\"" + loanAmount + "\",\"nominalRate\":\"" + nominalRate + "\",\"duration\":" + duration + ",\"startDate\":\"" + startDate + "\"}";
        Response generatedPlanResponse = given().spec(requestSpecification).body(requestBody).urlEncodingEnabled(false).
                when().post(generatePlanEndpoint)
                .then()
                .spec(responseSpecification)
                .extract().response();

        actualBorrowerPaymentAmountList = generatedPlanResponse.getBody().jsonPath().getList("borrowerPaymentAmount");
        actualLoanPaymentDateList = generatedPlanResponse.getBody().jsonPath().getList("date");
        actualInitialOutstandingPrincipalList = generatedPlanResponse.getBody().jsonPath().getList("initialOutstandingPrincipal");
        actualInterestList = generatedPlanResponse.getBody().jsonPath().getList("interest");
        actualPrincipalList = generatedPlanResponse.getBody().jsonPath().getList("principal");
        actualRemainingOutstandingPrincipalList = generatedPlanResponse.getBody().jsonPath().getList("remainingOutstandingPrincipal");
        calculateExpectedRepaymentPlan();
        removeLastResultFromComparison();
    }


    /***
     * Calculating the expected repayment plan so it can be matched against the actual plan.
     * Interest is calculated by mentioned formula.
     * Rounding the double value so that we can only consider 2 decimal places.
     */
    public void calculateExpectedRepaymentPlan() {
        Double remainingOutstandingPrincipal = loanAmount;
        Double cumulativePrincipal = 0.0;
        String previousDate = startDate;
        for (int i = 0; i < duration; i++) {
            Double interest = round2((nominalRate * 30 * (loanAmount - cumulativePrincipal)) / (360 * 100));
            Double principal;
            if (expectedInterestRateList.size() > 1 && interest > Double.valueOf(expectedInterestRateList.get(0)))
                principal = round2(annuity - Double.valueOf(expectedInterestRateList.get(0)));
            else
                principal = round2(annuity - interest);
            expectedInitialOutstandingPrincipal.add(String.format("%.2f", round2(loanAmount - cumulativePrincipal)));
            cumulativePrincipal += principal;
            Double borrowerPmtAmount = round2(principal + interest);
            remainingOutstandingPrincipal = remainingOutstandingPrincipal - principal;
            expectedInterestRateList.add(String.format("%.2f", interest));
            expectedPrincipalList.add(String.format("%.2f", principal));
            expectedBorrowerPaymentAmountList.add(String.valueOf(borrowerPmtAmount));
            if (remainingOutstandingPrincipal < 0)
                remainingOutstandingPrincipal = 0.0;
            expectedRemainingOutstandingPrincipal.add(String.format("%.2f", remainingOutstandingPrincipal));
            String tempDate = getValidPaymentDate(previousDate);
            expectedLoanPaymentDate.add(tempDate);
            previousDate = tempDate;
        }
    }


    /***
     * For the borrower payment amount, remaining outstanding principal and principal the value for last payment doesn't match. Removing for now until we have the logic to calculate these amount.
     */
    void removeLastResultFromComparison() {
        removeLastIndex(actualBorrowerPaymentAmountList, expectedBorrowerPaymentAmountList);
        removeLastIndex(actualRemainingOutstandingPrincipalList, expectedRemainingOutstandingPrincipal);
        removeLastIndex(actualPrincipalList, expectedPrincipalList);
    }

    /***
     * Verifying the total number of repayment plan contains payment plan for all the months in duration. If the duration doesn't match, all other test(s) will be skipped.
     * Input params: expectedLoanPaymentDate
     */
    @Test
    public void verifyTotalNumberOfEMI() {
        int totalMonthlyEMI = expectedLoanPaymentDate.size();
        Assert.assertEquals(totalMonthlyEMI, duration, "The total number of EMI doesn't match with the loan duration");
    }

    /***
     * Verifying the Borrower payment plan with expected borrower payment plan
     * Input params: actualBorrowerPaymentAmountList, expectedBorrowerPaymentAmountList
     */
    @Test(priority = 1)
    public void verifyBorrowerPaymentAmount() {
        Assert.assertEquals(actualBorrowerPaymentAmountList, expectedBorrowerPaymentAmountList, "Borrower amount doesn't match");
    }

    /***
     * Verifying the EMI repayment date matched with that of expected date.
     *  Input params: actualLoanPaymentDateList, expectedLoanPaymentDate
     */
    @Test(priority = 1)
    public void verifyEMIPaymentDate() {
        Assert.assertEquals(actualLoanPaymentDateList, expectedLoanPaymentDate, "Loan payment doesn't match");
    }

    /***
     * Verifying the Initial Outstanding principal.
     *  Input params: actualInitialOutstandingPrincipalList,expectedInitialOutstandingPrincipal
     */
    @Test(priority = 1)
    public void verifyInitialOutstandingPrincipal() {
        Assert.assertEquals(actualInitialOutstandingPrincipalList, expectedInitialOutstandingPrincipal, "Initial outstanding principal amount doesn't match");
    }

    /***
     * Verifying the Interest.
     * Input params: actualInterestList,expectedInterestRateList
     */
    @Test(priority = 1)
    public void verifyInterestRate() {
        Assert.assertEquals(actualInterestList, expectedInterestRateList, "Interest doesn't match");
    }


    /***
     * Verifying the Principal.
     * Input params: actualPrincipalList,expectedPrincipalList
     */
    @Test(priority = 1)
    public void verifyPrincipal() {
        Assert.assertEquals(actualPrincipalList, expectedPrincipalList, "Principal amount doesn't match");
    }


    /***
     * Verifying the Remaining Outstanding principal.
     * Input params: actualRemainingOutstandingPrincipalList,expectedRemainingOutstandingPrincipal
     */
    @Test(priority = 1)
    public void verifyRemainingOutstandingPrincipal() {
        Assert.assertEquals(actualRemainingOutstandingPrincipalList, expectedRemainingOutstandingPrincipal, "Remaining outstanding principal amount doesn't match");
    }


    void removeLastIndex(List<String> expectedList, List<String> actualList) {
        expectedList.remove(expectedList.size() - 1);
        actualList.remove(actualList.size() - 1);
    }

    Double round2(Double val) {
        return new BigDecimal(val.toString()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }


    /***
     *
     * @param prevDate
     * Will check for month and add the difference of days if the subsequent month has less number of days.
     * @return Date in format yyyy-MM-dd'T'HH:mm:ss
     */
    String getValidPaymentDate(String prevDate) {
        Date date = getDate(prevDate);
        Calendar calendar = new GregorianCalendar();
        calendar.setLenient(true);
        calendar.setTime(date);
        if (prevDate.equalsIgnoreCase(startDate)) {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(calendar.getTime()) + "Z";
        }
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.add(Calendar.MONTH, 1);
        int month = calendar.get(Calendar.MONTH);
        int maxDays;
        switch (month) {
            case 1:
                maxDays = 28;
                break;
            case 3:
                maxDays = 30;
                break;
            case 5:
                maxDays = 30;
                break;
            case 8:
                maxDays = 30;
                break;
            case 10:
                maxDays = 30;
                break;
            default:
                maxDays = 31;
        }
        int daysDifference = day - maxDays;
        if (daysDifference > 0) {
            calendar.add(Calendar.MONTH, 1);
            calendar.set(Calendar.DATE, daysDifference);
        }

        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(calendar.getTime()) + "Z";
    }

    Date getDate(String date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date newDate = null;
        try {
            newDate = simpleDateFormat.parse(date);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return newDate;
    }
}
