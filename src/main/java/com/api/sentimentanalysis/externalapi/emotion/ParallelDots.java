package com.api.sentimentanalysis.externalapi.emotion;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** This class contains a method to execute the sentiment analysis with ParallelDots API.
 *
 * @author Ariadna de Arriba
 */
public class ParallelDots implements EmotionAnalysisAPI
{
    private String apiKey;
    private String host = "https://apis.paralleldots.com/v5/";


    /** Constructor.
     *
     * @param apiKey Api-key to authorize the method.
     */
    public ParallelDots(String apiKey)
    {
        this.apiKey = apiKey;
        try
        {
            setUpCert("apis.paralleldots.com");
        } catch (Exception ex)
        {
        }
    }

    /** Set up ParallelDots certificate.
     *
     * @param hostname Host name for the call.
     * @throws Exception
     */
    private void setUpCert(String hostname) throws Exception
    {
        SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(hostname, 443);

        try
        {
            socket.startHandshake();
            socket.close();
            System.out.println("No errors, certificate is already trusted");
            return;
        } catch (SSLException e)
        {
            System.out.println("cert likely not found in keystore, will pull cert...");
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] password = "changeit".toCharArray();
        ks.load(null, password);

        SSLContext context = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
        SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
        context.init(null, new TrustManager[]{tm}, null);
        factory = context.getSocketFactory();

        socket = (SSLSocket) factory.createSocket(hostname, 443);
        try
        {
            socket.startHandshake();
        } catch (SSLException e)
        {
            //we should get to here
        }
        X509Certificate[] chain = tm.chain;
        if (chain == null)
        {
            System.out.println("Could not obtain server certificate chain");
            return;
        }

        X509Certificate cert = chain[0];
        String alias = hostname;
        ks.setCertificateEntry(alias, cert);

        System.out.println("saving file paralleldotscacerts to working dir");
        FileOutputStream fos = new FileOutputStream("paralleldotscacerts");
        ks.store(fos, password);
        fos.close();
    }

    /** Private method for saving trust manager.
     *
     */
    private static class SavingTrustManager implements X509TrustManager
    {
        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm)
        {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers()
        {

            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
        {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
        {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }

    /** Make a request to ParallelDots for sentiment analysis.
     *
     * @param text Text to analyze.
     * @return Returns a string that contains a json with each of six emotions weighted.
     * @throws NullPointerException {@link NullPointerException caused by an error during a call to the API and returns nothing.}
     * @throws IOException {@link IOException caused by an error in the API call. }
     */
    @Override
    public String getEmotion(String text) throws NullPointerException, IOException
    {
        if (this.apiKey != null)
        {
            String url = host + "emotion";
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                                            .addFormDataPart("api_key", this.apiKey)
                                            .addFormDataPart("text", text)
                                            .addFormDataPart("lang_code", "en")         // change "en" to "es" for spanish analysis
                                            .build();
            Request request = new Request.Builder().url(url).post(requestBody)
                                            .addHeader("cache-control", "no-cache")
                                            .build();
            Response response = client.newCall(request).execute();
            return response.body().string();
        } else
        {
            throw new NullPointerException("No api-key provided");
        }
    }
}