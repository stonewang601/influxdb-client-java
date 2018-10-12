/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.influxdata.flux.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.influxdata.flux.FluxClient;
import org.influxdata.flux.domain.FluxRecord;
import org.influxdata.flux.domain.FluxTable;
import org.influxdata.flux.impl.FluxCsvParser.FluxResponseConsumer;
import org.influxdata.flux.option.FluxConnectionOptions;
import org.influxdata.platform.Arguments;
import org.influxdata.platform.error.InfluxException;
import org.influxdata.platform.rest.Cancellable;
import org.influxdata.platform.rest.LogLevel;

import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSource;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * @author Jakub Bednar (bednar@github) (03/10/2018 14:20)
 */
public class FluxClientImpl extends AbstractFluxClient<FluxService> implements FluxClient {

    private static final Logger LOG = Logger.getLogger(FluxClientImpl.class.getName());

    private static final Runnable EMPTY_ACTION = () -> {

    };

    public FluxClientImpl(@Nonnull final FluxConnectionOptions options) {

        super(options, FluxService.class);
    }

    @Nonnull
    @Override
    public List<FluxTable> query(@Nonnull final String query) {

        Arguments.checkNonEmpty(query, "query");

        FluxResponseConsumerTable consumer = new FluxResponseConsumerTable();

        query(query, DEFAULT_DIALECT.toString(), consumer, ERROR_CONSUMER, EMPTY_ACTION, false);

        return consumer.getTables();
    }

    @Override
    public void query(@Nonnull final String query, @Nonnull final BiConsumer<Cancellable, FluxRecord> onNext) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onNext, "onNext");

        query(query, onNext, ERROR_CONSUMER);
    }

    @Override
    public void query(@Nonnull final String query,
                      @Nonnull final BiConsumer<Cancellable, FluxRecord> onNext,
                      @Nonnull final Consumer<? super Throwable> onError) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onNext, "onNext");
        Arguments.checkNotNull(onError, "onError");

        query(query, onNext, onError, EMPTY_ACTION);
    }

    @Override
    public void query(@Nonnull final String query,
                      @Nonnull final BiConsumer<Cancellable, FluxRecord> onNext,
                      @Nonnull final Consumer<? super Throwable> onError,
                      @Nonnull final Runnable onComplete) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onNext, "onNext");
        Arguments.checkNotNull(onError, "onError");
        Arguments.checkNotNull(onComplete, "onComplete");

        FluxResponseConsumer consumer = new FluxResponseConsumer() {
            @Override
            public void accept(final int index,
                               @Nonnull final Cancellable cancellable,
                               @Nonnull final FluxTable table) {
            }

            @Override
            public void accept(final int index,
                               @Nonnull final Cancellable cancellable,
                               @Nonnull final FluxRecord record) {
                onNext.accept(cancellable, record);
            }
        };

        query(query, DEFAULT_DIALECT.toString(), consumer, onError, onComplete, true);
    }

    @Nonnull
    @Override
    public String queryRaw(@Nonnull final String query) {

        Arguments.checkNonEmpty(query, "query");

        return queryRaw(query, (String) null);
    }

    @Nonnull
    @Override
    public String queryRaw(@Nonnull final String query, @Nullable final String dialect) {

        Arguments.checkNonEmpty(query, "query");

        List<String> rows = new ArrayList<>();

        BiConsumer<Cancellable, String> consumer = (cancellable, row) -> rows.add(row);

        queryRaw(query, dialect, consumer, ERROR_CONSUMER, EMPTY_ACTION, false);

        return String.join("\n", rows);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nonnull final BiConsumer<Cancellable, String> onResponse) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onResponse, "onNext");

        queryRaw(query, null, onResponse);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nullable final String dialect,
                         @Nonnull final BiConsumer<Cancellable, String> onNext) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onNext, "onNext");

        queryRaw(query, dialect, onNext, ERROR_CONSUMER);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nonnull final BiConsumer<Cancellable, String> onResponse,
                         @Nonnull final Consumer<? super Throwable> onError) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onResponse, "onNext");
        Arguments.checkNotNull(onError, "onError");

        queryRaw(query, onResponse, onError, EMPTY_ACTION);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nullable final String dialect,
                         @Nonnull final BiConsumer<Cancellable, String> onResponse,
                         @Nonnull final Consumer<? super Throwable> onError) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onResponse, "onNext");
        Arguments.checkNotNull(onError, "onError");

        queryRaw(query, dialect, onResponse, onError, EMPTY_ACTION);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nonnull final BiConsumer<Cancellable, String> onResponse,
                         @Nonnull final Consumer<? super Throwable> onError,
                         @Nonnull final Runnable onComplete) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onResponse, "onNext");
        Arguments.checkNotNull(onError, "onError");
        Arguments.checkNotNull(onComplete, "onComplete");

        queryRaw(query, null, onResponse, onError, onComplete);
    }

    @Override
    public void queryRaw(@Nonnull final String query,
                         @Nullable final String dialect,
                         @Nonnull final BiConsumer<Cancellable, String> onResponse,
                         @Nonnull final Consumer<? super Throwable> onError,
                         @Nonnull final Runnable onComplete) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(onResponse, "onNext");
        Arguments.checkNotNull(onError, "onError");
        Arguments.checkNotNull(onComplete, "onComplete");

        queryRaw(query, dialect, onResponse, onError, onComplete, true);
    }

    @Nonnull
    @Override
    public Boolean ping() {

        Call<ResponseBody> ping = fluxService.ping();

        try {
            return ping.execute().isSuccessful();
        } catch (IOException e) {

            LOG.log(Level.WARNING, "Ping request wasn't successful", e);
            return false;
        }
    }

    @Override
    @Nonnull
    public String version() {

        Call<ResponseBody> ping = fluxService.ping();

        try {
            Response<ResponseBody> execute = ping.execute();
            return getVersion(execute);
        } catch (IOException e) {
            throw new InfluxException(e);
        }
    }

    @Nonnull
    @Override
    public LogLevel getLogLevel() {
        HttpLoggingInterceptor.Level level = this.loggingInterceptor.getLevel();

        return LogLevel.valueOf(level.name());
    }

    @Nonnull
    @Override
    public FluxClient setLogLevel(@Nonnull final LogLevel logLevel) {

        Arguments.checkNotNull(logLevel, "LogLevel");

        this.loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.valueOf(logLevel.name()));

        return this;
    }

    private void query(@Nonnull final String query,
                       @Nonnull final String dialect,
                       @Nonnull final FluxResponseConsumer responseConsumer,
                       @Nonnull final Consumer<? super Throwable> onError,
                       @Nonnull final Runnable onComplete,
                       @Nonnull final Boolean asynchronously) {

        BiConsumer<Cancellable, BufferedSource> consumer = (cancellable, bufferedSource) -> {
            try {
                fluxCsvParser.parseFluxResponse(bufferedSource, cancellable, responseConsumer);
            } catch (IOException e) {
                onError.accept(e);
            }
        };

        query(query, dialect, consumer, onError, onComplete, asynchronously);
    }

    private void queryRaw(@Nonnull final String query,
                          @Nullable final String dialect,
                          @Nonnull final BiConsumer<Cancellable, String> onResponse,
                          @Nonnull final Consumer<? super Throwable> onError,
                          @Nonnull final Runnable onComplete,
                          @Nonnull final Boolean asynchronously) {

        BiConsumer<Cancellable, BufferedSource> consumer = (cancellable, bufferedSource) -> {

            try {
                parseFluxResponseToLines(line -> onResponse.accept(cancellable, line), cancellable, bufferedSource);
            } catch (IOException e) {
                catchOrPropagateException(e, onError);
            }
        };

        query(query, dialect, consumer, onError, onComplete, asynchronously);
    }

    private void query(@Nonnull final String query,
                       @Nullable final String dialect,
                       @Nonnull final BiConsumer<Cancellable, BufferedSource> consumer,
                       @Nonnull final Consumer<? super Throwable> onError,
                       @Nonnull final Runnable onComplete,
                       @Nonnull final Boolean asynchronously) {

        Arguments.checkNonEmpty(query, "query");
        Arguments.checkNotNull(consumer, "consumer");
        Arguments.checkNotNull(onError, "onError");
        Arguments.checkNotNull(onComplete, "onComplete");
        Arguments.checkNotNull(asynchronously, "asynchronously");

        Call<ResponseBody> call = fluxService.query(createBody(dialect, query));

        DefaultCancellable cancellable = new DefaultCancellable();

        Callback<ResponseBody> callback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(@Nonnull final Call<ResponseBody> call,
                                   @Nonnull final Response<ResponseBody> response) {

                if (!response.isSuccessful()) {
                    onError.accept(responseToError(response));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    return;
                }

                try {
                    BufferedSource source = body.source();

                    //
                    // Source has data => parse
                    //
                    while (!source.exhausted() && !cancellable.wasCancelled) {

                        consumer.accept(cancellable, source);
                    }

                    if (!cancellable.wasCancelled) {
                        onComplete.run();
                    }

                } catch (Exception e) {
                    catchOrPropagateException(e, onError);

                } finally {

                    body.close();
                }
            }

            @Override
            public void onFailure(@Nonnull final Call<ResponseBody> call, @Nonnull final Throwable throwable) {
                onError.accept(throwable);
            }
        };

        if (asynchronously) {
            call.enqueue(callback);
        } else {

            Response<ResponseBody> response;
            try {
                response = call.execute();
                callback.onResponse(call, response);
            } catch (IOException e) {
                catchOrPropagateException(e, onError);
            }
        }

    }

    private class DefaultCancellable implements Cancellable {

        private volatile boolean wasCancelled = false;

        @Override
        public void cancel() {
            wasCancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return wasCancelled;
        }
    }
}