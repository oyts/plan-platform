package cn.zijun701.plan.mobile;

/** Data-only notification destinations; no credentials or arbitrary navigation. */
final class ReminderRoutes {
    private ReminderRoutes() {}
    static boolean valid(String path) {
        if(path==null)return false;
        String[] parts=path.split("\\?",-1);
        if(parts.length!=2)return false;
        String date;
        if(parts[0].equals("/reflection")) {
            if(!parts[1].matches("kind=(DAY|WEEK)&date=\\d{4}-\\d{2}-\\d{2}"))return false;
            date=parts[1].substring(parts[1].indexOf("&date=")+6);
        } else {
            if(!parts[0].equals("/todos")&&!parts[0].matches("/plans/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))return false;
            if(!parts[1].matches("date=\\d{4}-\\d{2}-\\d{2}"))return false;
            date=parts[1].substring(5);
        }
        // minSdk 24: do not require java.time (API 26) just to validate a tap.
        int year=Integer.parseInt(date.substring(0,4)),month=Integer.parseInt(date.substring(5,7)),day=Integer.parseInt(date.substring(8,10));
        if(year<1||month<1||month>12)return false;
        int[] days={31,year%400==0||year%4==0&&year%100!=0?29:28,31,30,31,30,31,31,30,31,30,31};
        return day>=1&&day<=days[month-1];
    }
}
