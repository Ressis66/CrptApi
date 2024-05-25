import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;


public class CrptApi {
  private final ReentrantLock lock = new ReentrantLock();
  private final long limit;
  private final TimeUnit timeUnit;
  private long lastRequestTime;
  private long requestsInInterval;

  private class CrptDoc {
    private Description description;
    private String doc_id;
    private String doc_status;
    private String doc_type;
    private Boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private LocalDate production_date;
    private String production_type;
    private List<Product> products;
    private LocalDate reg_date;
    private String reg_number;

    public CrptDoc(Description description, String doc_id, String doc_status, String doc_type,
                   Boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                   LocalDate production_date, String production_type, List<Product> products,
                   LocalDate reg_date, String reg_number) {
      this.description = description;
      this.doc_id = doc_id;
      this.doc_status = doc_status;
      this.doc_type = doc_type;
      this.importRequest = importRequest;
      this.owner_inn = owner_inn;
      this.participant_inn = participant_inn;
      this.producer_inn = producer_inn;
      this.production_date = production_date;
      this.production_type = production_type;
      this.products = products;
      this.reg_date = reg_date;
      this.reg_number = reg_number;
    }

    @Override
    public String toString() {
      return "CrptDoc{" +
          "description=" + description +
          ", doc_id='" + doc_id + '\'' +
          ", doc_status='" + doc_status + '\'' +
          ", doc_type='" + doc_type + '\'' +
          ", importRequest=" + importRequest +
          ", owner_inn='" + owner_inn + '\'' +
          ", participant_inn='" + participant_inn + '\'' +
          ", producer_inn='" + producer_inn + '\'' +
          ", production_date=" + production_date +
          ", production_type='" + production_type + '\'' +
          ", products=" + products +
          ", reg_date=" + reg_date +
          ", reg_number='" + reg_number + '\'' +
          '}';
    }
  }
  private class Product {
    private String certificate_document;
    private LocalDate certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private LocalDate production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;

    public Product(String certificate_document, LocalDate certificate_document_date,
                   String certificate_document_number, String owner_inn, String producer_inn,
                   LocalDate production_date, String tnved_code, String uit_code, String uitu_code) {
      this.certificate_document = certificate_document;
      this.certificate_document_date = certificate_document_date;
      this.certificate_document_number = certificate_document_number;
      this.owner_inn = owner_inn;
      this.producer_inn = producer_inn;
      this.production_date = production_date;
      this.tnved_code = tnved_code;
      this.uit_code = uit_code;
      this.uitu_code = uitu_code;
    }

    @Override
    public String toString() {
      return "Product{" +
          "certificate_document='" + certificate_document + '\'' +
          ", certificate_document_date=" + certificate_document_date +
          ", certificate_document_number='" + certificate_document_number + '\'' +
          ", owner_inn='" + owner_inn + '\'' +
          ", producer_inn='" + producer_inn + '\'' +
          ", production_date=" + production_date +
          ", tnved_code='" + tnved_code + '\'' +
          ", uit_code='" + uit_code + '\'' +
          ", uitu_code='" + uitu_code + '\'' +
          '}';
    }
  }

  private class Description {
    private String participantInn;

    public Description(String participantInn) {
      this.participantInn = participantInn;
    }

    @Override
    public String toString() {
      return "Description{" +
          "participantInn='" + participantInn + '\'' +
          '}';
    }
  }
  public CrptApi(TimeUnit timeUnit, int requestLimit) {
    this.timeUnit = Objects.requireNonNull(timeUnit);
    this.limit = timeUnit.toSeconds(timeUnit.convert(requestLimit, TimeUnit.SECONDS));
    this.lastRequestTime = System.currentTimeMillis() / 1000; // Convert to seconds for simplicity
    this.requestsInInterval = 0;
  }

  public void createDocument(CrptDoc document, String signature) throws InterruptedException, IOException {
    if (!tryAcquire()) {
      throw new IllegalStateException("API call limit exceeded");
    }

    try {
      HttpClient client = HttpClient.newBuilder()
          .version(Version.HTTP_2)
          .followRedirects(Redirect.NORMAL)
          .build();

      ObjectMapper mapper = new ObjectMapper();
      String jsonDocument = mapper.writeValueAsString(document);
      String jsonSignature = signature;

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
          .header("X-Signature", jsonSignature)
          .build();

      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      System.out.println("Response: " + response.body());
    } finally {
      release();
    }
  }

  private boolean tryAcquire() throws InterruptedException {
    long currentTime = System.currentTimeMillis() / 1000;
    long elapsedTime = currentTime - lastRequestTime;
    if (elapsedTime >= timeUnit.toSeconds(1)) {
      lastRequestTime = currentTime;
      requestsInInterval = 0;
    } else {
      elapsedTime = timeUnit.toSeconds(1) - elapsedTime;
    }

    if (requestsInInterval >= limit) {
      lock.lockInterruptibly();
      try {
        while (requestsInInterval >= limit && !Thread.interrupted()) {
          lock.wait(elapsedTime);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      } finally {
        lock.unlock();
      }
    }

    requestsInInterval++;
    return true;
  }

  private void release() {
    lock.lock();
    try {
      lock.notifyAll();
    } finally {
      lock.unlock();
    }
  }
}