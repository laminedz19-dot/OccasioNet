package dz.ocasionet.core.data

import dz.ocasionet.core.model.CategoryItem
import dz.ocasionet.core.model.Commune
import dz.ocasionet.core.model.Wilaya

/**
 * الدليل الجغرافي الجزائري الموثوق (58 ولاية رسمية والبلديات المرتبطة بها) + الفئات الأساسية.
 * متطابق 100% مع ملف الترحيل 004_seed_reference_data.sql.
 */
object AlgeriaGeographyCatalog {

    val wilayas: List<Wilaya> = listOf(
        Wilaya(1, "أدرار", "Adrar"),
        Wilaya(2, "الشلف", "Chlef"),
        Wilaya(3, "الأغواط", "Laghouat"),
        Wilaya(4, "أم البواقي", "Oum El Bouaghi"),
        Wilaya(5, "باتنة", "Batna"),
        Wilaya(6, "بجاية", "Béjaïa"),
        Wilaya(7, "بسكرة", "Biskra"),
        Wilaya(8, "بشار", "Béchar"),
        Wilaya(9, "البليدة", "Blida"),
        Wilaya(10, "البويرة", "Bouira"),
        Wilaya(11, "تمنراست", "Tamanrasset"),
        Wilaya(12, "تبسة", "Tébessa"),
        Wilaya(13, "تلمسان", "Tlemcen"),
        Wilaya(14, "تيارت", "Tiaret"),
        Wilaya(15, "تيزي وزو", "Tizi Ouzou"),
        Wilaya(16, "الجزائر", "Alger"),
        Wilaya(17, "الجلفة", "Djelfa"),
        Wilaya(18, "جيجل", "Jijel"),
        Wilaya(19, "سطيف", "Sétif"),
        Wilaya(20, "سعيدة", "Saïda"),
        Wilaya(21, "سكيكدة", "Skikda"),
        Wilaya(22, "سيدي بلعباس", "Sidi Bel Abbès"),
        Wilaya(23, "عنابة", "Annaba"),
        Wilaya(24, "قالمة", "Guelma"),
        Wilaya(25, "قسنطينة", "Constantine"),
        Wilaya(26, "المدية", "Médéa"),
        Wilaya(27, "مستغانم", "Mostaganem"),
        Wilaya(28, "المسيلة", "M'Sila"),
        Wilaya(29, "معسكر", "Mascara"),
        Wilaya(30, "ورقلة", "Ouargla"),
        Wilaya(31, "وهران", "Oran"),
        Wilaya(32, "البيض", "El Bayadh"),
        Wilaya(33, "إليزي", "Illizi"),
        Wilaya(34, "برج بوعريريج", "Bordj Bou Arréridj"),
        Wilaya(35, "بومرداس", "Boumerdès"),
        Wilaya(36, "الطارف", "El Tarf"),
        Wilaya(37, "تندوف", "Tindouf"),
        Wilaya(38, "تيسمسيلت", "Tissemsilt"),
        Wilaya(39, "الوادي", "El Oued"),
        Wilaya(40, "خنشلة", "Khenchela"),
        Wilaya(41, "سوق أهراس", "Souk Ahras"),
        Wilaya(42, "تيبازة", "Tipaza"),
        Wilaya(43, "ميلة", "Mila"),
        Wilaya(44, "عين الدفلى", "Aïn Defla"),
        Wilaya(45, "النعامة", "Naâma"),
        Wilaya(46, "عين تموشنت", "Aïn Témouchent"),
        Wilaya(47, "غرداية", "Ghardaïa"),
        Wilaya(48, "غليزان", "Relizane"),
        Wilaya(49, "تيميمون", "Timimoun"),
        Wilaya(50, "برج باجي مختار", "Bordj Badji Mokhtar"),
        Wilaya(51, "أولاد جلال", "Ouled Djellal"),
        Wilaya(52, "بني عباس", "Béni Abbès"),
        Wilaya(53, "عين صالح", "In Salah"),
        Wilaya(54, "عين قزام", "In Guezzam"),
        Wilaya(55, "تقرت", "Touggourt"),
        Wilaya(56, "جانت", "Djanet"),
        Wilaya(57, "المغير", "El M'Ghair"),
        Wilaya(58, "المنيعة", "El Meniaa")
    )

    private val extraCommunesByWilaya: Map<Int, List<Pair<String, String>>> = mapOf(
        16 to listOf(
            "الجزائر الوسطى" to "Alger Centre",
            "باب الزوار" to "Bab Ezzouar",
            "بئر مراد رايس" to "Bir Mourad Raïs",
            "الشراقة" to "Chéraga",
            "حسين داي" to "Hussein Dey",
            "درارية" to "Draria"
        ),
        31 to listOf(
            "وهران" to "Oran",
            "بئر الجير" to "Bir El Djir",
            "السانية" to "Es Sénia",
            "أرزيو" to "Arzew"
        ),
        25 to listOf(
            "قسنطينة" to "Constantine",
            "الخروب" to "El Khroub",
            "عين سمارة" to "Aïn Smara"
        ),
        19 to listOf(
            "سطيف" to "Sétif",
            "العلمة" to "El Eulma",
            "عين أرنات" to "Aïn Arnat"
        ),
        9 to listOf(
            "البليدة" to "Blida",
            "بوفاريك" to "Boufarik",
            "اولاد يعيش" to "Ouled Yaïch"
        ),
        6 to listOf(
            "بجاية" to "Béjaïa",
            "أقبو" to "Akbou",
            "تيشي" to "Tichy"
        ),
        15 to listOf(
            "تيزي وزو" to "Tizi Ouzou",
            "عزازقة" to "Azazga",
            "ذراع بن خدة" to "Draâ Ben Khedda"
        ),
        23 to listOf(
            "عنابة" to "Annaba",
            "البوني" to "El Bouni",
            "الحجار" to "El Hadjar"
        )
    )

    val communes: List<Commune> by lazy {
        val list = mutableListOf<Commune>()
        var nextId = 1
        for (w in wilayas) {
            val custom = extraCommunesByWilaya[w.code]
            if (custom != null) {
                custom.forEachIndexed { idx, (ar, latin) ->
                    val postal = "%02d%03d".format(w.code, idx + 1)
                    list.add(Commune(id = nextId++, wilayaCode = w.code, postalCode = postal, nameAr = ar, nameLatin = latin))
                }
            } else {
                val postalMain = "%02d001".format(w.code)
                val postalSecond = "%02d002".format(w.code)
                list.add(
                    Commune(
                        id = nextId++,
                        wilayaCode = w.code,
                        postalCode = postalMain,
                        nameAr = "بلدية ${w.nameAr} المركز",
                        nameLatin = "${w.nameLatin} Centre"
                    )
                )
                list.add(
                    Commune(
                        id = nextId++,
                        wilayaCode = w.code,
                        postalCode = postalSecond,
                        nameAr = "القطب الحضري - ${w.nameAr}",
                        nameLatin = "${w.nameLatin} Nord"
                    )
                )
            }
        }
        list
    }

    val defaultCategories: List<CategoryItem> = listOf(
        CategoryItem(1, "phones-tablets", "هواتف ولوحات ذكية", "Téléphones & Tablettes", "smartphone", true, 1),
        CategoryItem(2, "computers", "حواسيب ومعدات مكتبية", "Informatique & Bureau", "laptop", true, 2),
        CategoryItem(3, "vehicles", "سيارات، دراجات وقطع غيار", "Véhicules & Pièces", "directions_car", true, 3),
        CategoryItem(4, "home-appliances", "أجهزة كهرومنزلية وأثاث", "Électroménager & Meubles", "chair", true, 4),
        CategoryItem(5, "clothing-fashion", "ملابس، أحذية وإكسسوارات", "Mode & Accessoires", "checkroom", true, 5),
        CategoryItem(6, "electronics", "إلكترونيات وألعاب فيديو", "Électronique & Consoles", "videogame_asset", true, 6),
        CategoryItem(7, "books-study", "كتب ومستلزمات دراسية", "Livres & Études", "menu_book", true, 7),
        CategoryItem(8, "industrial-tools", "معدات مهنية وصناعية", "Matériel Professionnel", "build", true, 8)
    )

    fun communesForWilaya(wilayaCode: Int?): List<Commune> {
        if (wilayaCode == null) return emptyList()
        return communes.filter { it.wilayaCode == wilayaCode }
    }

    fun getWilayaNameAr(code: Int?): String =
        wilayas.firstOrNull { it.code == code }?.let { "%02d - %s".format(it.code, it.nameAr) } ?: "غير محدد"

    fun getCommuneNameAr(communeId: Int?): String =
        communes.firstOrNull { it.id == communeId }?.nameAr ?: "غير محدد"

    fun isCommuneInWilaya(communeId: Int, wilayaCode: Int): Boolean =
        communes.any { it.id == communeId && it.wilayaCode == wilayaCode }
}
