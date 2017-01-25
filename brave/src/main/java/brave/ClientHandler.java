package brave;

import brave.parser.Parser;
import brave.parser.TagsParser;
import com.google.auto.value.AutoValue;
import zipkin.Constants;
import zipkin.Endpoint;

@AutoValue
public abstract class ClientHandler<Req, Resp> {
  public static abstract class Config<Req, Resp> {

    protected Parser<Req, String> spanNameParser() {
      return r -> "";
    }

    protected Parser<Req, Endpoint> requestAddressParser() {
      return r -> null;
    }

    protected abstract TagsParser<Req> requestTagsParser();

    protected Parser<Resp, Endpoint> responseAddressParser() {
      return r -> null;
    }

    protected abstract TagsParser<Resp> responseTagsParser();
  }

  public static <Req, Resp> ClientHandler<Req, Resp> create(Config<Req, Resp> config) {
    return new AutoValue_ClientHandler.Builder()
        .spanNameParser(config.spanNameParser())
        .requestTagsParser(config.requestTagsParser())
        .requestAddressParser(config.requestAddressParser())
        .responseTagsParser(config.responseTagsParser())
        .responseAddressParser(config.responseAddressParser())
        .build();
  }

  @AutoValue.Builder
  public interface Builder<Req, Resp> {
    Builder<Req, Resp> spanNameParser(Parser<Req, String> spanNameParser);

    Builder<Req, Resp> requestAddressParser(Parser<Req, Endpoint> requestAddressParser);

    Builder<Req, Resp> requestTagsParser(TagsParser<Req> requestTagsParser);

    Builder<Req, Resp> responseAddressParser(Parser<Resp, Endpoint> responseAddressParser);

    Builder<Req, Resp> responseTagsParser(TagsParser<Resp> responseTagsParser);

    ClientHandler<Req, Resp> build();
  }

  abstract Parser<Req, String> spanNameParser();

  abstract Parser<Req, Endpoint> requestAddressParser();

  abstract TagsParser<Req> requestTagsParser();

  abstract Parser<Resp, Endpoint> responseAddressParser();

  abstract TagsParser<Resp> responseTagsParser();

  public Req handleSend(Req request, Span span) {
    if (span.isNoop()) return request;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.CLIENT).name(spanNameParser().parse(request));
    requestTagsParser().addTagsToSpan(request, span);
    Endpoint serverAddress = requestAddressParser().parse(request);
    if (serverAddress != null) {
      span.remoteEndpoint(serverAddress);
    }
    span.start();
    return request;
  }

  public Resp handleReceive(Resp response, Span span) {
    if (span.isNoop()) return response;

    try {
      Endpoint serverAddress = responseAddressParser().parse(response);
      if (serverAddress != null) {
        span.remoteEndpoint(serverAddress);
      }
      responseTagsParser().addTagsToSpan(response, span);
    } finally {
      span.finish();
    }
    return response;
  }

  public <T extends Throwable> T handleError(T throwable, Span span) {
    if (span.isNoop()) return throwable;

    try {
      String message = throwable.getMessage();
      if (message == null) message = throwable.getClass().getSimpleName();
      span.tag(Constants.ERROR, message);
      return throwable;
    } finally {
      span.finish();
    }
  }

  ClientHandler() {
  }
}