package org.wikipedia.descriptions;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import org.wikipedia.dataclient.WikiSite;
import org.wikipedia.dataclient.retrofit.MwCachedService;
import org.wikipedia.dataclient.retrofit.RetrofitException;
import org.wikipedia.login.User;
import org.wikipedia.page.PageTitle;
import org.wikipedia.server.mwapi.MwServiceError;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

/**
 * Data Client to submit a new or updated description to wikidata.org.
 */
class DescriptionEditClient {
    private static final String ANONYMOUS_TOKEN = "+\\";
    private static final WikiSite WIKI_DATA_SITE = new WikiSite("www.wikidata.org", "");

    @NonNull private final MwCachedService<Service> cachedService
            = new MwCachedService<>(Service.class);

    public interface Callback {
        void success(@NonNull Call<DescriptionEdit> call);
        void abusefilter(@NonNull Call<DescriptionEdit> call, boolean disallowed);
        void failure(@NonNull Call<DescriptionEdit> call, @NonNull Throwable caught);
    }

    /**
     * Submit a new value for the Wikidata description associated with the given Wikipedia page.
     *
     * @param pageTitle   specifies the Wikipedia page the Wikidata item is linked to
     * @param description the new value for the Wikidata description
     * @param cb          called when this is done successfully or failed
     * @return Call object which can be used to cancel the request
     */
    public Call<DescriptionEdit> request(@NonNull PageTitle pageTitle,
                                         @NonNull String description,
                                         @NonNull Callback cb) {
        return request(cachedService.service(WIKI_DATA_SITE), pageTitle, description,
                pageTitle.getWikiSite().languageCode(), User.isLoggedIn(), cb);
    }

    @SuppressWarnings("WeakerAccess") @VisibleForTesting
    Call<DescriptionEdit> request(@NonNull Service service,
                                  @NonNull final PageTitle pageTitle,
                                  @NonNull final String description,
                                  @NonNull final String languageCode,
                                  final boolean loggedIn,
                                  @NonNull final Callback cb) {

        Call<DescriptionEdit> call = service.edit(languageCode, languageCode + "wiki",
                pageTitle.getPrefixedText(), description, ANONYMOUS_TOKEN, null,
                /* TODO: loggedIn ? "user" : */ null);
        call.enqueue(new retrofit2.Callback<DescriptionEdit>() {
            @Override
            public void onResponse(Call<DescriptionEdit> call,
                                   Response<DescriptionEdit> response) {
                if (response.isSuccessful()) {
                    final DescriptionEdit body = response.body();
                    if (body.editWasSuccessful()) {
                        cb.success(call);
                    } else if (body.hasError()) {
                        handleError(call, body, cb);
                    } else {
                        cb.failure(call,
                                RetrofitException.unexpectedError(new RuntimeException(
                                "Received unrecognized description edit response")));
                    }
                } else {
                    cb.failure(call,
                            RetrofitException.httpError(response, cachedService.retrofit()));
                }
            }

            @Override
            public void onFailure(Call<DescriptionEdit> call, Throwable t) {
                cb.failure(call, t);
            }
        });
        return call;
    }

    private void handleError(@NonNull Call<DescriptionEdit> call, @NonNull DescriptionEdit body,
                             @NonNull Callback cb) {
        MwServiceError error = body.getError();
        if (error != null && error.hasMessageName("abusefilter-disallowed")) {
            cb.abusefilter(call, true);
        } else if (error != null && error.hasMessageName("abusefilter-warning")) {
            cb.abusefilter(call, false);
        } else {
            String info = body.info();
            RuntimeException exception = new RuntimeException(info != null
                    ? info : "An unknown error occurred");
            cb.failure(call, RetrofitException.unexpectedError(exception));
        }
    }

    @VisibleForTesting interface Service {
        @FormUrlEncoded
        @POST("w/api.php?action=wbsetdescription&format=json")
        Call<DescriptionEdit> edit(@NonNull @Field("language") String language,
                                   @NonNull @Field("site") String site,
                                   @NonNull @Field("title") String title,
                                   @NonNull @Field("value") String newDescription,
                                   @NonNull @Field("token") String token,
                                   @Nullable @Field("centralauthtoken") String centralAuthToken,
                                   @Nullable @Field("assert") String user
        );
    }
}
