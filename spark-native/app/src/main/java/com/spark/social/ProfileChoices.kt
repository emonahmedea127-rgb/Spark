package com.spark.social

val profileChoices:Map<String,List<String>> = mapOf(
 "category" to listOf("Digital creator","Content creator","Artist","Musician","Entrepreneur","Blogger","Video creator","Photographer","Writer","Gamer","Public figure","Education","Web developer","Designer","Business owner","Student"),
 "ai_creator" to listOf("Yes","No"),
 "gender" to listOf("Male","Female","Non-binary","Prefer not to say"),
 "relationship" to listOf("Single","In a relationship","Engaged","Married","In a civil partnership","In a domestic partnership","In an open relationship","It's complicated","Separated","Divorced","Widowed","Prefer not to say"),
 "languages" to listOf("Bangla","English","Hindi","Urdu","Arabic","Spanish","French","German","Portuguese","Chinese","Japanese","Korean","Tamil","Turkish","Italian","Russian"),
 "work" to listOf("Self-employed","Freelance","Upwork","Fiverr","Google","Microsoft","Meta","Amazon","BRAC","Grameenphone","Banglalink","Robi","bKash","Nagad"),
 "education" to listOf("National University of Bangladesh","University of Dhaka","Jahangirnagar University","University of Rajshahi","University of Chittagong","Bangladesh University of Engineering and Technology","North South University","BRAC University","Daffodil International University","Bangladesh Open University"),
 "hobby" to listOf("Travelling","Photography","Reading","Gardening","Cooking","Fishing","Cycling","Hiking","Drawing","Writing","Gaming","Fitness","Dancing","Volunteering"),
 "music" to listOf("Bangla music","Pop","Rock","Classical","Jazz","Hip-hop","Folk","Country","Electronic","Instrumental","Rabindra Sangeet","Nazrul Geeti"),
 "tv" to listOf("Money Heist","Breaking Bad","Friends","Game of Thrones","Stranger Things","The Office","Sherlock","Dark","The Crown"),
 "film" to listOf("Avengers: Endgame","Interstellar","Inception","The Dark Knight","Titanic","Avatar","3 Idiots","The Shawshank Redemption","Pather Panchali"),
 "game" to listOf("Clash of Clans","PUBG Mobile","Free Fire","Minecraft","Call of Duty","EA Sports FC","eFootball","Fortnite","Valorant","Chess"),
 "sport" to listOf("Football","Cricket","Badminton","Basketball","Tennis","Swimming","Bangladesh cricket team","Argentina","Brazil","FC Barcelona","Real Madrid","Lionel Messi","Cristiano Ronaldo"),
 "family_role" to listOf("Mother","Father","Brother","Sister","Son","Daughter","Spouse","Cousin","Grandmother","Grandfather","Aunt","Uncle","Other relative"),
 "employment" to listOf("Full-time","Part-time","Self-employed","Freelance","Contract","Internship","Apprenticeship","Volunteer"),
 "degree" to listOf("Secondary school","Higher secondary","Diploma","Bachelor's degree","Master's degree","Doctorate","Professional certificate"),
 "platform" to listOf("Facebook","Instagram","YouTube","LinkedIn","TikTok","X","GitHub","Threads","Other"),
 "country_code" to listOf("+880 Bangladesh","+91 India","+92 Pakistan","+1 USA / Canada","+44 United Kingdom","+966 Saudi Arabia","+971 UAE","+60 Malaysia","+65 Singapore","+61 Australia"),
 "offer_type" to listOf("Promotion","Affiliate link","Discount code","Service offer")
)
val fixedProfileChoices=setOf("category","ai_creator","gender","relationship")
val mapProfileKinds=setOf("city","hometown","travel")
fun profileDetailSummary(kind:String,metadata:org.json.JSONObject,legacy:String=""):String {
 fun v(k:String)=metadata.s(k)
 val values=when(kind){
  "work"->listOf(v("role"),v("employment"),listOf(v("start"),if(metadata.optBoolean("current"))"Present" else v("end")).filter{it.isNotBlank()}.joinToString(" – "))
  "education"->listOf(v("degree"),v("subject"),v("year").takeIf{it.isNotBlank()}?.let{"Class of $it"}?:"")
  "family"->listOf(v("family_role"))
  "social"->listOf(v("platform"))
  "offer"->listOf(v("offer_type"),v("url"))
  else->emptyList()
 }
 return values.filter{it.isNotBlank()}.joinToString(" · ").ifBlank{legacy}
}
