package dev.newsflash.i18n;

import java.util.List;

public final class NewsFlashMessages {
    private final boolean english;

    public NewsFlashMessages(String language) {
        this.english = "en".equalsIgnoreCase(language);
    }

    public boolean english() {
        return english;
    }

    public String providerCount(int count) {
        return english ? "NewsFlash providers: " + count : "NewsFlash 取得元: " + count;
    }

    public String providerSchedule(String name, int initialDelaySeconds, int pollIntervalMinutes) {
        if (english) {
            return "- " + name + ": first check after " + initialDelaySeconds + " second(s), then every " + pollIntervalMinutes + " minute(s)";
        }
        return "- " + name + ": 初回 " + initialDelaySeconds + " 秒後、その後 " + pollIntervalMinutes + " 分ごと";
    }

    public String p2pStatus(boolean enabled, int minScale, boolean tsunamiEnabled, boolean eewEnabled) {
        if (english) {
            return "- P2PQuake: " + status(enabled) + ", earthquake min scale " + minScale
                + ", tsunami " + status(tsunamiEnabled) + ", eew " + status(eewEnabled);
        }
        return "- P2P地震情報: " + status(enabled) + ", 地震 最小震度 " + minScale
            + ", 津波 " + status(tsunamiEnabled) + ", 緊急地震速報 " + status(eewEnabled);
    }

    public String rssStatus(boolean enabled, int feedCount) {
        return "- RSS/Atom: " + status(enabled) + (english ? ", feeds " : ", フィード ") + feedCount;
    }

    public String rssFeedStatus(String id, boolean enabled, boolean filterEnabled) {
        return "  - " + id + ": " + status(enabled) + (english ? ", filter " : ", フィルター ") + status(filterEnabled);
    }

    public String reloaded() {
        return english ? "NewsFlash reloaded." : "NewsFlash を再読み込みしました。";
    }

    public String reloaded(String target) {
        return english ? "NewsFlash reloaded: " + target : "NewsFlash を再読み込みしました: " + target;
    }

    public String unknownReloadTarget(String target) {
        return english ? "Unknown NewsFlash reload target: " + target : "不明な reload 対象です: " + target;
    }

    public String checkStarted() {
        return english ? "NewsFlash check started." : "NewsFlash の確認を開始しました。";
    }

    public String checkStarted(String target) {
        return english ? "NewsFlash check started: " + target : "NewsFlash の確認を開始しました: " + target;
    }

    public String unknownCheckTarget(String target) {
        return english ? "Unknown or unsupported NewsFlash check target: " + target : "不明または未対応の check 対象です: " + target;
    }

    public String mofaSource() {
        return english ? "MOFA Overseas Safety" : "外務省 海外安全情報";
    }

    public String p2pSource() {
        return english ? "P2PQuake" : "P2P地震情報";
    }

    public String quakeType() {
        return english ? "Earthquake" : "地震情報";
    }

    public String tsunamiType() {
        return english ? "Tsunami Forecast" : "津波予報";
    }

    public String eewType() {
        return english ? "Emergency Earthquake Warning" : "緊急地震速報";
    }

    public String quakeTitle(String scale, String hypocenter) {
        return english ? "Earthquake: Max intensity " + scale + " " + hypocenter : "地震情報: 最大震度" + scale + " " + hypocenter;
    }

    public String quakeLead(String time, String magnitude, String depth, String tsunami, String targetAreaText) {
        if (english) {
            return "Time: " + time + " / M" + magnitude + " / Depth " + depth + " / Tsunami: " + tsunami + targetAreaText;
        }
        return "発生時刻: " + time + " / M" + magnitude + " / 深さ" + depth + " / 津波: " + tsunami + targetAreaText;
    }

    public String tsunamiTitle(String grade) {
        return english ? "Tsunami: " + grade : "津波情報: " + grade;
    }

    public String targetAreas(List<String> targets) {
        return (english ? "Target areas: " : "対象地域: ") + String.join(", ", targets);
    }

    public String eewTitle(String hypocenter) {
        return english ? "Emergency Earthquake Warning: " + hypocenter : "緊急地震速報（警報）: " + hypocenter;
    }

    public String eewLead(String serial, String scale, String magnitude, String depth, String areaText) {
        if (english) {
            return "Report " + serial + " / Max predicted intensity " + scale + " / M" + magnitude + " / Depth " + depth + areaText;
        }
        return "第" + serial + "報 / 最大予測震度" + scale + " / M" + magnitude + " / 深さ" + depth + areaText;
    }

    public String targetAreaText(String scale) {
        return english ? " / Target-area max intensity: " + scale : " / 対象地域最大震度: " + scale;
    }

    public String targetAreaText(List<String> areas) {
        return (english ? " / Target areas: " : " / 対象地域: ") + String.join(", ", areas);
    }

    public String matchedMaxScale(String scale) {
        return english ? "Max intensity " + scale : "最大震度" + scale;
    }

    public String matchedTargetMaxScale(String scale) {
        return english ? "Target-area max intensity " + scale : "対象地域最大震度" + scale;
    }

    public String areaScale(String pref, String area, String scale) {
        return english ? pref + " " + area + " intensity " + scale : pref + " " + area + " 震度" + scale;
    }

    public String unknown() {
        return english ? "unknown" : "不明";
    }

    public String unknownHypocenter() {
        return english ? "Unknown hypocenter" : "震源不明";
    }

    public String unknownArea() {
        return english ? "Unknown area" : "地域不明";
    }

    public String depth(int depth) {
        return depth < 0 ? unknown() : depth + "km";
    }

    public String scaleLabel(int scale) {
        if (!english) {
            return switch (scale) {
                case 10 -> "1";
                case 20 -> "2";
                case 30 -> "3";
                case 40 -> "4";
                case 45 -> "5弱";
                case 46 -> "5弱以上";
                case 50 -> "5強";
                case 55 -> "6弱";
                case 60 -> "6強";
                case 70 -> "7";
                case 99 -> "程度以上";
                default -> "不明";
            };
        }
        return switch (scale) {
            case 10 -> "1";
            case 20 -> "2";
            case 30 -> "3";
            case 40 -> "4";
            case 45 -> "5 lower";
            case 46 -> "5 lower or higher";
            case 50 -> "5 upper";
            case 55 -> "6 lower";
            case 60 -> "6 upper";
            case 70 -> "7";
            case 99 -> "or higher";
            default -> "unknown";
        };
    }

    public String tsunamiLabel(String tsunami) {
        return switch (tsunami) {
            case "None" -> english ? "None" : "なし";
            case "Checking" -> english ? "Checking" : "調査中";
            case "NonEffective" -> english ? "Minor sea-level changes" : "若干の海面変動";
            case "Watch" -> english ? "Tsunami Advisory" : "津波注意報";
            case "Warning" -> english ? "Tsunami Warning" : "津波警報";
            default -> unknown();
        };
    }

    public String tsunamiGradeLabel(String grade) {
        return switch (grade) {
            case "MajorWarning" -> english ? "Major Tsunami Warning" : "大津波警報";
            case "Warning" -> english ? "Tsunami Warning" : "津波警報";
            case "Watch" -> english ? "Tsunami Advisory" : "津波注意報";
            default -> english ? "Tsunami Information" : "津波情報";
        };
    }

    private String status(boolean enabled) {
        if (english) {
            return enabled ? "enabled" : "disabled";
        }
        return enabled ? "有効" : "無効";
    }
}
