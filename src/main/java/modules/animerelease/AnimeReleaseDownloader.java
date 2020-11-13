package modules.animerelease;

import constants.Language;
import core.internet.HttpResponse;
import core.internet.InternetCache;
import core.utils.StringUtil;
import core.utils.TimeUtil;
import modules.PostBundle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AnimeReleaseDownloader {

    public static PostBundle<AnimeReleasePost> getPosts(Locale locale, String newestPostId, String filterString) throws InterruptedException, ExecutionException {
        final List<String> filter = Arrays.stream(filterString.split(",")).map(StringUtil::trimString).collect(Collectors.toList());
        String downloadUrl = "https://feeds.feedburner.com/crunchyroll/rss/anime";

        HttpResponse httpResponse = InternetCache.getData(downloadUrl, 9 * 60).get();
        String postString = httpResponse.getContent().get();

        JSONArray postArray = XML.toJSONObject(postString).getJSONObject("rss").getJSONObject("channel").getJSONArray("item");
        ArrayList<AnimeReleasePost> postList = new ArrayList<>();
        List<AnimeReleasePost> animeReleasePosts = getAnimeReleasePostList(postArray, locale);

        List<Integer> currentUsedIds = (newestPostId == null || newestPostId.isEmpty()) ? new ArrayList<>() : Arrays.stream(newestPostId.split("\\|")).map(Integer::parseInt).collect(Collectors.toList());
        ArrayList<String> newUsedIds = new ArrayList<>();

        for (AnimeReleasePost post : animeReleasePosts) {
            boolean dub = post.getAnime().endsWith("Dub)") || post.getAnime().endsWith("(Russian)");
            boolean validDub = post.getAnime().endsWith("(English Dub)") ||
                    (post.getAnime().endsWith("(Russian)") && StringUtil.getLanguage(locale) == Language.RU) ||
                    (post.getAnime().endsWith("(German Dub)") && StringUtil.getLanguage(locale) == Language.DE);

            boolean ok = postPassesFilter(post, filter) && (!dub || validDub);
            if (ok) {
                if (!currentUsedIds.contains(post.getId()) &&
                        (postList.size() == 0 || newestPostId != null)
                ) postList.add(post);
                newUsedIds.add(String.valueOf(post.getId()));
            }
        }

        StringBuilder sb = new StringBuilder();
        newUsedIds.forEach(str -> sb.append("|").append(str));
        if (sb.length() > 0) newestPostId = sb.substring(1);
        else newestPostId = "";

        return new PostBundle<>(postList, newestPostId);
    }

    private static boolean postPassesFilter(AnimeReleasePost post, List<String> filter) {
        return filter.size() == 0 ||
                filter.get(0).equalsIgnoreCase("all") ||
                filter.stream().anyMatch(f -> StringUtil.stringContainsVague(post.getAnime(), f)) ||
                filter.stream().anyMatch(f -> StringUtil.stringContainsVague(post.getUrl(), f));
    }

    private static List<AnimeReleasePost> getAnimeReleasePostList(JSONArray data, Locale locale) {
        ArrayList<AnimeReleasePost> list = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            AnimeReleasePost post = parseEpisode(data.getJSONObject(i), locale);
            AnimeReleasePost nextPost = null;
            AnimeReleasePost tempPost;

            while (i + 1 < data.length() && (tempPost = parseEpisode(data.getJSONObject(i + 1), locale)).getAnime().equals(post.getAnime())) {
                nextPost = tempPost;
                i++;
            }

            if (nextPost != null) {
                String episode = null;
                if (nextPost.getEpisode().isPresent() && post.getEpisode().isPresent()) {
                    episode = String.format("%s - %s", nextPost.getEpisode().get(), post.getEpisode().get());
                }

                post = new AnimeReleasePost(
                        post.getAnime(),
                        "",
                        episode,
                        null,
                        post.getThumbnail(),
                        post.getInstant(),
                        post.getUrl(),
                        post.getId()
                );
            }

            list.add(post);
        }

        return list;
    }

    private static AnimeReleasePost parseEpisode(JSONObject data, Locale locale) {
        String anime = data.getString("title");
        if (anime.contains(" - Folge ")) anime = anime.substring(0, anime.indexOf(" - Folge "));
        else if (anime.contains(" - Episode ")) anime = anime.substring(0, anime.indexOf(" - Episode "));
        else anime = data.getString("crunchyroll:seriesTitle");

        String description = data.getString("description");
        description = description.substring(description.indexOf("<br />") + "<br />".length());
        if (description.contains("<img")) description = description.split("<img")[0];

        String episode = null;
        if (data.has("crunchyroll:episodeNumber")) {
            try {
                episode = StringUtil.numToString(data.getInt("crunchyroll:episodeNumber"));
            } catch (Exception e) {
                //Ignore
                episode = data.getString("crunchyroll:episodeNumber");
            }
        }

        String episodeTitle;
        try {
            episodeTitle = data.getString("crunchyroll:episodeTitle");
        } catch (Exception e) {
            //Ignore
            double value = data.getDouble("crunchyroll:episodeTitle");
            if (((int) value) == value) episodeTitle = String.valueOf((int) value);
            else episodeTitle = String.valueOf(value);
        }

        String thumbnail = "";
        if (data.has("media:thumbnail"))
            thumbnail = data.getJSONArray("media:thumbnail").getJSONObject(0).getString("url");
        Instant date = TimeUtil.parseDateString2(data.getString("crunchyroll:premiumPubDate"));
        String url = data.getString("link").replace("/de/", "/");
        int id = data.getInt("crunchyroll:mediaId");

        return new AnimeReleasePost(anime, description, episode, episodeTitle, thumbnail, date, url, id);
    }

}
