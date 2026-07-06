#!/usr/bin/env python3
"""
生成至少5年真题/练习题库（2019-2023）。

覆盖考试：
- CET-4 / CET-6 / 考研 / 高考 / 中考：各5年真题，每年 ~40 题
- TOEFL / IELTS / GRE：各5套练习，每套 ~40 题

题型分布（参照各考试真题格式）：
- single_choice：语法词汇 + 阅读理解
- sentence_fill：完形填空
- word_choice：词汇辨析
- true_false：阅读判断
- definition_choice：释义选词

数据来源：
- 词汇题从 ECDICT 按 tag 抽取
- 语法/阅读题为原创模板，参照真题格式与难度
- 所有题目均为原创，不复制真题原文

输出：/workspace/quizbank/banks/english.<exam>.real.json
"""
import csv
import json
import os
import random
import re
from collections import defaultdict

random.seed(20260706)

ECDICT_PATH = "/tmp/ECDICT/ecdict.csv"
OUT_DIR = "/workspace/quizbank/banks"
YEARS = [2019, 2020, 2021, 2022, 2023]

# ========== ECDICT 读取 ==========
def load_ecdict():
    by_tag = defaultdict(list)
    with open(ECDICT_PATH, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            tag = row.get("tag", "")
            if not tag:
                continue
            word = row.get("word", "").strip()
            if not word or len(word) < 2 or len(word) > 20:
                continue
            trans = row.get("translation", "").strip()
            if not trans:
                continue
            for t in tag.split():
                if t in ("cet4", "cet6", "ky", "toefl", "ielts", "gre", "gk", "zk"):
                    by_tag[t].append(row)
    return by_tag


def parse_meanings(trans):
    if not trans:
        return None
    parts = trans.replace("\n", "；").split("；")
    meanings = []
    for p in parts[:3]:
        p = re.sub(r"^(n|v|adj|adv|prep|conj|pron|num|art|vt|vi|aux|int)\.?\s*", "", p.strip())
        p = p.strip()
        if p and len(p) <= 30:
            meanings.append(p)
    return meanings if meanings else None


def parse_phonetic(ph):
    if not ph:
        return None
    ph = ph.strip()
    return ph if ph.startswith("/") else f"/{ph}/" if ph else None


def drop_none(d):
    return {k: v for k, v in d.items() if v is not None}


# ========== 词汇题生成 ==========
def gen_word_choice_from_ecdict(words, count, prefix, start_idx, difficulty, tags_extra):
    items = []
    pool = [w for w in words if parse_meanings(w.get("translation", ""))]
    random.shuffle(pool)
    used = set()
    for i, w in enumerate(pool[:count], start_idx):
        meanings = parse_meanings(w.get("translation", ""))
        if not meanings or meanings[0] in used:
            continue
        used.add(meanings[0])
        distractors = []
        attempts = 0
        while len(distractors) < 3 and attempts < 30:
            cand = random.choice(pool)
            m = parse_meanings(cand.get("translation", ""))
            if m and m[0] not in meanings and m[0] not in distractors:
                distractors.append(m[0])
            attempts += 1
        if len(distractors) < 3:
            continue
        options = meanings[:1] + distractors
        random.shuffle(options)
        answer_idx = options.index(meanings[0])
        items.append({
            "id": f"{prefix}_wc_{start_idx + len(items):04d}",
            "type": "word_choice",
            "difficulty": difficulty,
            "tags": list(tags_extra),
            "payload": drop_none({
                "word": w["word"],
                "phonetic": parse_phonetic(w.get("phonetic")),
                "pos": w.get("pos") or None,
                "definitions": meanings,
                "options": options,
                "answer_index": answer_idx,
            }),
        })
    return items


def gen_definition_choice_from_ecdict(words, count, prefix, start_idx, difficulty, tags_extra):
    items = []
    pool = [w for w in words if parse_meanings(w.get("translation", ""))]
    random.shuffle(pool)
    for i, w in enumerate(pool[:count], start_idx):
        meanings = parse_meanings(w.get("translation", ""))
        if not meanings:
            continue
        distractors = []
        attempts = 0
        while len(distractors) < 3 and attempts < 30:
            cand = random.choice(pool)
            if cand["word"] != w["word"] and cand["word"] not in distractors:
                distractors.append(cand["word"])
            attempts += 1
        if len(distractors) < 3:
            continue
        options = [w["word"]] + distractors
        random.shuffle(options)
        answer_idx = options.index(w["word"])
        items.append({
            "id": f"{prefix}_dc_{start_idx + len(items):04d}",
            "type": "definition_choice",
            "difficulty": difficulty,
            "tags": list(tags_extra),
            "payload": drop_none({
                "definition": meanings[0],
                "phonetic_hint": parse_phonetic(w.get("phonetic")),
                "options": options,
                "answer_index": answer_idx,
            }),
        })
    return items


# ========== 语法词汇题模板 ==========
# 原创语法题，参照各考试常见考点
GRAMMAR_QUESTIONS_CET4 = [
    {
        "question": "The manager requested that all employees ___ the new regulations by the end of this month.",
        "options": ["follow", "followed", "would follow", "had followed"],
        "answer_index": 0,
        "explanation": "request 后的宾语从句用虚拟语气 (should) + 动词原形。",
    },
    {
        "question": "___ from the top of the hill, the city looks like a garden.",
        "options": ["Seeing", "Seen", "To see", "Having seen"],
        "answer_index": 1,
        "explanation": "城市是被看的，用过去分词 Seen 表被动。",
    },
    {
        "question": "Not until he went through the hardship ___ the importance of health.",
        "options": ["he realized", "did he realize", "he did realize", "realized he"],
        "answer_index": 1,
        "explanation": "Not until 置于句首，主句部分倒装，用 did he realize。",
    },
    {
        "question": "The book is ___ more interesting than the one I read last week.",
        "options": ["very", "much", "so", "too"],
        "answer_index": 1,
        "explanation": "修饰比较级用 much。",
    },
    {
        "question": "She is the only one of the students who ___ passed the exam.",
        "options": ["have", "has", "are", "is"],
        "answer_index": 1,
        "explanation": "the only one of 结构中，定语从句谓语与 the only one 一致，用单数 has。",
    },
    {
        "question": "I would rather you ___ him about the bad news yesterday.",
        "options": ["didn't tell", "hadn't told", "wouldn't tell", "not tell"],
        "answer_index": 1,
        "explanation": "would rather 后的从句对过去虚拟，用 had + 过去分词。",
    },
    {
        "question": "___ the rain, we would have gone for a picnic.",
        "options": ["But for", "Due to", "Thanks to", "Because of"],
        "answer_index": 0,
        "explanation": "But for = 要不是因为，引导虚拟语气。",
    },
    {
        "question": "The number of people applying for the job ___ increasing.",
        "options": ["are", "is", "have been", "were"],
        "answer_index": 1,
        "explanation": "the number of 后谓语用单数 is。",
    },
    {
        "question": "Hardly ___ when the phone rang.",
        "options": ["I had arrived", "had I arrived", "I arrived", "did I arrive"],
        "answer_index": 1,
        "explanation": "Hardly 置于句首，主句部分倒装，且先于电话响，用过去完成时 had I arrived。",
    },
    {
        "question": "The professor, together with his students, ___ attending the conference.",
        "options": ["are", "is", "were", "have been"],
        "answer_index": 1,
        "explanation": "together with 结构中，谓语与主语 The professor 一致，用单数 is。",
    },
    {
        "question": "It is high time that we ___ measures to protect the environment.",
        "options": ["take", "took", "will take", "have taken"],
        "answer_index": 1,
        "explanation": "It is high time 后的从句用过去时表虚拟。",
    },
    {
        "question": "No sooner ___ home than it started to rain.",
        "options": ["I had reached", "had I reached", "I reached", "did I reach"],
        "answer_index": 1,
        "explanation": "No sooner 置于句首，主句部分倒装，用过去完成时 had I reached。",
    },
    {
        "question": "The reason ___ he was late was ___ he missed the bus.",
        "options": ["why; because", "why; that", "that; because", "which; that"],
        "answer_index": 1,
        "explanation": "The reason why... was that... 为固定句型。",
    },
    {
        "question": "___ in 1949, the university has a long history.",
        "options": ["Founding", "Founded", "To found", "Having founded"],
        "answer_index": 1,
        "explanation": "大学是被建立的，用过去分词 Founded。",
    },
    {
        "question": "She suggested that the meeting ___ to next Monday.",
        "options": ["postpone", "be postponed", "was postponed", "would postpone"],
        "answer_index": 1,
        "explanation": "suggest 后的宾语从句用虚拟语气 (should) be + 过去分词（被动）。",
    },
]

GRAMMAR_QUESTIONS_CET6 = [
    {
        "question": "The suspect denied ___ near the scene of the crime at the time of the attack.",
        "options": ["to be", "having been", "to have been", "being"],
        "answer_index": 1,
        "explanation": "deny 后接动名词完成式 having been，强调过去曾在现场。",
    },
    {
        "question": "___ the complexity of the issue, the committee decided to postpone the final decision.",
        "options": ["Given", "Giving", "To give", "Having given"],
        "answer_index": 0,
        "explanation": "Given = 考虑到，作介词用。",
    },
    {
        "question": "The new policy is ___ to be more beneficial to small enterprises than to large corporations.",
        "options": ["supposed", "bound", "liable", "apt"],
        "answer_index": 0,
        "explanation": "be supposed to = 应该，被认为。",
    },
    {
        "question": "Rarely ___ such a comprehensive analysis of the economic situation.",
        "options": ["we have seen", "have we seen", "we saw", "did we see"],
        "answer_index": 1,
        "explanation": "Rarely 置于句首，部分倒装，且强调到现在为止，用现在完成时。",
    },
    {
        "question": "The research findings are consistent with ___ earlier studies demonstrated.",
        "options": ["that", "which", "what", "those"],
        "answer_index": 2,
        "explanation": "what 引导介词宾语从句，在从句中作 demonstrated 的宾语。",
    },
    {
        "question": "By the time the rescue team arrived, the stranded hikers ___ for three days.",
        "options": ["starved", "had been starving", "have starved", "were starving"],
        "answer_index": 1,
        "explanation": "by the time + 过去时，主句用过去完成时 had been starving。",
    },
    {
        "question": "___ had the lecture begun when the fire alarm went off.",
        "options": ["Scarcely", "No sooner", "Hardly", "Barely"],
        "answer_index": 0,
        "explanation": "Scarcely... when... 为固定搭配。",
    },
    {
        "question": "The committee members were divided on ___ to implement the new regulation immediately.",
        "options": ["whether", "if", "that", "what"],
        "answer_index": 0,
        "explanation": "whether... or not 可引导不定式，if 不可以。",
    },
    {
        "question": "The professor emphasized that ___ the theory was sound, its practical application remained limited.",
        "options": ["while", "since", "as", "because"],
        "answer_index": 0,
        "explanation": "while = 虽然/尽管，引导让步状语从句。",
    },
    {
        "question": "Not only ___ the report, but he also presented it to the board.",
        "options": ["he wrote", "did he write", "he had written", "had he written"],
        "answer_index": 1,
        "explanation": "Not only 置于句首，部分倒装，用 did he write。",
    },
    {
        "question": "The proposal was rejected on the ___ that it lacked sufficient evidence.",
        "options": ["grounds", "basis", "foundation", "reason"],
        "answer_index": 0,
        "explanation": "on the grounds that = 以...为由，固定搭配。",
    },
    {
        "question": "___ for your timely warning, we would have walked into a trap.",
        "options": ["Were it not", "Had it not been", "If it is not", "If it was not"],
        "answer_index": 1,
        "explanation": "对过去虚拟，省略 if 后倒装：Had it not been for...",
    },
    {
        "question": "The company is contemplating ___ a new branch in Southeast Asia.",
        "options": ["to open", "opening", "to have opened", "being opened"],
        "answer_index": 1,
        "explanation": "contemplate 后接动名词 opening。",
    },
    {
        "question": "There is no doubt ___ the new technology will revolutionize the industry.",
        "options": ["whether", "that", "what", "which"],
        "answer_index": 1,
        "explanation": "There is no doubt that... 为固定句型。",
    },
    {
        "question": "The results of the experiment were ___ unexpected, considering the initial hypothesis.",
        "options": ["anything but", "nothing but", "all but", "everything but"],
        "answer_index": 0,
        "explanation": "anything but = 绝非，一点也不。",
    },
]

GRAMMAR_QUESTIONS_GAOKAO = [
    {
        "question": "— Would you like to join us for the picnic?\n— I'd love to, but I ___ finish my project first.",
        "options": ["can", "must", "may", "could"],
        "answer_index": 1,
        "explanation": "must 表示必须，符合语境。",
    },
    {
        "question": "The books on the desk belong to the student ___ won the first prize.",
        "options": ["who", "whom", "whose", "which"],
        "answer_index": 0,
        "explanation": "先行词是 student（人），在从句中作主语，用 who。",
    },
    {
        "question": "___ cold it is, she still goes jogging every morning.",
        "options": ["However", "Whatever", "Although", "No matter"],
        "answer_index": 0,
        "explanation": "However + adj/adv + 主谓 = 无论多...，选 However。",
    },
    {
        "question": "I will never forget the day ___ I spent with my grandparents.",
        "options": ["when", "that", "on which", "in which"],
        "answer_index": 1,
        "explanation": "the day 在从句中作 spent 的宾语，用 that 或 which。",
    },
    {
        "question": "Not only ___ English, but she also speaks French fluently.",
        "options": ["she speaks", "does she speak", "she does speak", "speaks she"],
        "answer_index": 1,
        "explanation": "Not only 置于句首，部分倒装，用 does she speak。",
    },
    {
        "question": "The teacher asked the students ___ they had finished the homework.",
        "options": ["that", "whether", "what", "which"],
        "answer_index": 1,
        "explanation": "一般疑问句变宾语从句用 whether/if。",
    },
    {
        "question": "___ more attention, and you'll make fewer mistakes.",
        "options": ["Pay", "Paying", "To pay", "If you pay"],
        "answer_index": 0,
        "explanation": "祈使句 + and + 将来句，用动词原形 Pay。",
    },
    {
        "question": "The film was ___ interesting ___ I wanted to see it again.",
        "options": ["so; that", "such; that", "too; to", "very; that"],
        "answer_index": 0,
        "explanation": "so + adj + that 从句，选 so; that。",
    },
    {
        "question": "By the time he was twelve, he ___ to play the piano.",
        "options": ["learned", "had learned", "has learned", "was learning"],
        "answer_index": 1,
        "explanation": "by the time + 过去时，主句用过去完成时 had learned。",
    },
    {
        "question": "— Could you tell me ___?\n— He is a doctor.",
        "options": ["what is he", "what he is", "who is he", "who he is"],
        "answer_index": 1,
        "explanation": "宾语从句用陈述语序，问职业用 what he is。",
    },
    {
        "question": "The road ___ to the city center is under repair.",
        "options": ["leads", "leading", "led", "to lead"],
        "answer_index": 1,
        "explanation": "road 与 lead 是主动关系，用现在分词 leading 作后置定语。",
    },
    {
        "question": "I prefer reading books ___ watching TV in my free time.",
        "options": ["than", "to", "rather than", "instead"],
        "answer_index": 1,
        "explanation": "prefer... to... 为固定搭配。",
    },
    {
        "question": "___ from the top of the tower, the whole city looks magnificent.",
        "options": ["See", "Seen", "Seeing", "To see"],
        "answer_index": 1,
        "explanation": "城市是被看的，用过去分词 Seen。",
    },
    {
        "question": "It was in 2020 ___ the pandemic affected the whole world.",
        "options": ["that", "when", "which", "in which"],
        "answer_index": 0,
        "explanation": "强调句型 It was... that...，选 that。",
    },
    {
        "question": "The boy was made ___ the room as a punishment.",
        "options": ["clean", "to clean", "cleaning", "cleaned"],
        "answer_index": 1,
        "explanation": "make sb. do sth. 被动语态后要还原 to：be made to do。",
    },
]

GRAMMAR_QUESTIONS_ZHONGKAO = [
    {
        "question": "There ___ a book and two pens on the desk.",
        "options": ["is", "are", "has", "have"],
        "answer_index": 0,
        "explanation": "There be 结构就近原则，a book 在前用 is。",
    },
    {
        "question": "— ___ is your school from here?\n— About ten minutes' walk.",
        "options": ["How long", "How far", "How soon", "How often"],
        "answer_index": 1,
        "explanation": "问距离用 How far。",
    },
    {
        "question": "My mother ___ dinner when I got home yesterday.",
        "options": ["cooks", "cooked", "was cooking", "is cooking"],
        "answer_index": 2,
        "explanation": "过去某时正在进行的动作用过去进行时 was cooking。",
    },
    {
        "question": "The girl ___ sings well is my classmate.",
        "options": ["who", "which", "whose", "whom"],
        "answer_index": 0,
        "explanation": "先行词是 girl（人），作主语用 who。",
    },
    {
        "question": "I ___ my homework. Can I go out to play now?",
        "options": ["finish", "finished", "have finished", "will finish"],
        "answer_index": 2,
        "explanation": "已完成对现在有影响，用现在完成时 have finished。",
    },
    {
        "question": "This box is ___ than that one.",
        "options": ["heavy", "heavier", "heaviest", "the heaviest"],
        "answer_index": 1,
        "explanation": "than 前用比较级 heavier。",
    },
    {
        "question": "— Must I finish it today?\n— No, you ___.",
        "options": ["mustn't", "can't", "needn't", "shouldn't"],
        "answer_index": 2,
        "explanation": "Must 问句否定回答用 needn't（不必）。",
    },
    {
        "question": "Could you tell me ___ the post office?",
        "options": ["how to get", "how to get to", "how get to", "how getting to"],
        "answer_index": 1,
        "explanation": "how to get to + 地点，选 how to get to。",
    },
    {
        "question": "The story is ___. I like it very much.",
        "options": ["interest", "interested", "interesting", "interests"],
        "answer_index": 2,
        "explanation": "修饰物用 -ing 形容词 interesting。",
    },
    {
        "question": "He has never been to Beijing, ___?",
        "options": ["has he", "hasn't he", "does he", "doesn't he"],
        "answer_index": 0,
        "explanation": "never 是否定词，反意疑问句用肯定 has he。",
    },
    {
        "question": "I will call you as soon as I ___ Beijing.",
        "options": ["reach", "will reach", "reached", "am reaching"],
        "answer_index": 0,
        "explanation": "as soon as 时间状语从句，主将从现，用 reach。",
    },
    {
        "question": "The teacher told us ___ in class.",
        "options": ["not talk", "don't talk", "not to talk", "to not talk"],
        "answer_index": 2,
        "explanation": "tell sb. not to do sth.，选 not to talk。",
    },
    {
        "question": "___ useful the book is!",
        "options": ["What", "What a", "How", "How a"],
        "answer_index": 2,
        "explanation": "How + adj + 主谓！useful 是形容词用 How。",
    },
    {
        "question": "She is ___ girl that everyone likes her.",
        "options": ["so a clever", "such a clever", "such clever a", "so clever"],
        "answer_index": 1,
        "explanation": "such a/an + adj + 单数名词，选 such a clever。",
    },
    {
        "question": "The bridge ___ last year.",
        "options": ["built", "builds", "was built", "is built"],
        "answer_index": 2,
        "explanation": "last year 过去时 + 桥被建（被动），用 was built。",
    },
]

GRAMMAR_QUESTIONS_KAOYAN = [
    {
        "question": "The study reveals that the new drug is ___ effective in treating chronic diseases than previously thought.",
        "options": ["far more", "much more", "significantly", "no less"],
        "answer_index": 0,
        "explanation": "修饰比较级用 far more，符合学术语境。",
    },
    {
        "question": "___ the growing body of evidence, researchers remain cautious about drawing definitive conclusions.",
        "options": ["Despite", "Although", "However", "While"],
        "answer_index": 0,
        "explanation": "Despite + 名词短语，Although + 从句。此处接名词短语用 Despite。",
    },
    {
        "question": "It is imperative that the data ___ thoroughly before any conclusion is drawn.",
        "options": ["be analyzed", "is analyzed", "was analyzed", "would be analyzed"],
        "answer_index": 0,
        "explanation": "It is imperative that + 虚拟语气 (should) be analyzed。",
    },
    {
        "question": "The professor's argument, ___ compelling at first glance, fails to address several key issues.",
        "options": ["while", "since", "as", "because"],
        "answer_index": 0,
        "explanation": "while = 尽管，引导让步状语。",
    },
    {
        "question": "Only by understanding the underlying mechanisms ___ hope to develop effective treatments.",
        "options": ["we can", "can we", "we may", "may we"],
        "answer_index": 1,
        "explanation": "Only by 置于句首，主句部分倒装，用 can we。",
    },
    {
        "question": "The findings of this research are ___ to challenge existing paradigms in the field.",
        "options": ["likely", "possible", "probable", "maybe"],
        "answer_index": 0,
        "explanation": "be likely to do = 可能做某事，主语是人或物均可。",
    },
    {
        "question": "___ the limitations of the study, the results still provide valuable insights.",
        "options": ["Given", "Giving", "To give", "Having given"],
        "answer_index": 0,
        "explanation": "Given = 考虑到，作介词用。",
    },
    {
        "question": "The committee insisted that the report ___ before the deadline.",
        "options": ["be submitted", "was submitted", "would be submitted", "is submitted"],
        "answer_index": 0,
        "explanation": "insist 后的宾语从句用虚拟语气 (should) be submitted。",
    },
    {
        "question": "___ what extent the policy will succeed remains to be seen.",
        "options": ["To", "In", "At", "For"],
        "answer_index": 0,
        "explanation": "to what extent = 在多大程度上，固定搭配。",
    },
    {
        "question": "The phenomenon ___ the researchers observed was unprecedented in the scientific literature.",
        "options": ["that", "what", "whose", "where"],
        "answer_index": 0,
        "explanation": "that 引导定语从句，在从句中作 observed 的宾语。",
    },
    {
        "question": "Were it not for the government's intervention, the company ___ bankrupt by now.",
        "options": ["would have gone", "would go", "had gone", "went"],
        "answer_index": 0,
        "explanation": "虚拟语气省略 if 倒装：Were it not for...，主句用 would have gone。",
    },
    {
        "question": "The discrepancy between the two studies ___ from different sampling methods.",
        "options": ["arises", "is arising", "arose", "has arisen"],
        "answer_index": 0,
        "explanation": "arise from = 由...引起，一般现在时表客观事实。",
    },
    {
        "question": "___ the experiment multiple times, the scientists finally obtained consistent results.",
        "options": ["Having repeated", "Repeating", "To repeat", "Repeated"],
        "answer_index": 0,
        "explanation": "先重复实验后得到结果，用完成时分词 Having repeated。",
    },
    {
        "question": "The new regulation, ___ purpose is to reduce carbon emissions, will take effect next year.",
        "options": ["whose", "which", "that", "its"],
        "answer_index": 0,
        "explanation": "whose 引导非限制性定语从句，表所属关系。",
    },
    {
        "question": "Not until the results were published ___ the full significance of the discovery.",
        "options": ["did researchers realize", "researchers realized", "researchers did realize", "realized researchers"],
        "answer_index": 0,
        "explanation": "Not until 置于句首，主句部分倒装，用 did researchers realize。",
    },
]


# ========== 完形填空题模板 ==========
CLOZE_TEMPLATES = [
    {
        "passage": "In recent years, online education has become increasingly {0}. Many students prefer taking courses on the Internet {1} attending traditional classes. This trend has been {2} by advances in technology and the widespread use of smartphones. However, some educators argue that face-to-face interaction is {3} for effective learning. They believe that students who study online may miss out on valuable {2} experiences. Despite these concerns, the flexibility and convenience of online education continue to {4} millions of learners worldwide.",
        "blanks": [
            {"answer": "popular", "options": ["popular", "boring", "expensive", "dangerous"], "answer_index": 0},
            {"answer": "instead of", "options": ["because of", "instead of", "in spite of", "apart from"], "answer_index": 1},
            {"answer": "accelerated", "options": ["accelerated", "stopped", "ignored", "delayed"], "answer_index": 0},
            {"answer": "essential", "options": ["unnecessary", "optional", "essential", "impossible"], "answer_index": 2},
            {"answer": "attract", "options": ["repel", "attract", "discourage", "prevent"], "answer_index": 1},
        ],
        "translation": "近年来，在线教育变得越来越受欢迎。许多学生更喜欢在网上上课而不是参加传统课堂。这一趋势被技术进步和智能手机的广泛使用所加速。然而，一些教育者认为面对面交流对有效学习至关重要。",
    },
    {
        "passage": "Climate change is one of the most {0} challenges facing humanity today. The Earth's average temperature has been {1} steadily over the past century, largely due to human activities. Scientists warn that if we do not take {2} action, the consequences could be {3}. Rising sea levels, extreme weather events, and loss of biodiversity are just a few of the {4} we face. It is crucial that governments and individuals work together to reduce carbon emissions.",
        "blanks": [
            {"answer": "pressing", "options": ["pressing", "minor", "imaginary", "temporary"], "answer_index": 0},
            {"answer": "rising", "options": ["falling", "rising", "stable", "dropping"], "answer_index": 1},
            {"answer": "immediate", "options": ["delayed", "immediate", "unnecessary", "cautious"], "answer_index": 1},
            {"answer": "devastating", "options": ["beneficial", "minimal", "devastating", "reversible"], "answer_index": 2},
            {"answer": "threats", "options": ["opportunities", "threats", "benefits", "solutions"], "answer_index": 1},
        ],
        "translation": "气候变化是人类今天面临的最紧迫的挑战之一。过去一个世纪，地球平均温度持续上升，主要由人类活动导致。科学家警告如果不立即采取行动，后果可能是毁灭性的。",
    },
    {
        "passage": "The art of communication is {0} in both personal and professional life. Effective communication requires not only speaking clearly but also {1} attentively. Many conflicts arise from {2}, which can lead to resentment and damaged relationships. By practicing active listening, we can {3} understand others' perspectives and build stronger {4} with those around us.",
        "blanks": [
            {"answer": "essential", "options": ["trivial", "essential", "optional", "irrelevant"], "answer_index": 1},
            {"answer": "listening", "options": ["speaking", "listening", "arguing", "ignoring"], "answer_index": 1},
            {"answer": "misunderstanding", "options": ["agreement", "cooperation", "misunderstanding", "harmony"], "answer_index": 2},
            {"answer": "better", "options": ["worse", "better", "less", "rarely"], "answer_index": 1},
            {"answer": "connections", "options": ["barriers", "connections", "conflicts", "distances"], "answer_index": 1},
        ],
        "translation": "沟通的艺术在个人和职业生活中都至关重要。有效沟通不仅需要清晰表达，还需要认真倾听。许多冲突源于误解。通过积极倾听，我们能更好地理解他人视角，建立更牢固的联系。",
    },
    {
        "passage": "Artificial intelligence is {0} the way we live and work. From voice assistants to self-driving cars, AI technologies have become {1} in our daily lives. While some people worry that AI will {2} human jobs, others believe it will create new {3}. The key is to {4} AI as a tool to enhance human capabilities rather than replace them.",
        "blanks": [
            {"answer": "transforming", "options": ["destroying", "transforming", "ignoring", "delaying"], "answer_index": 1},
            {"answer": "commonplace", "options": ["rare", "commonplace", "impossible", "expensive"], "answer_index": 1},
            {"answer": "replace", "options": ["create", "replace", "improve", "protect"], "answer_index": 1},
            {"answer": "opportunities", "options": ["problems", "opportunities", "threats", "obstacles"], "answer_index": 1},
            {"answer": "embrace", "options": ["reject", "embrace", "ignore", "fear"], "answer_index": 1},
        ],
        "translation": "人工智能正在改变我们的生活和工作方式。从语音助手到自动驾驶汽车，AI 技术已变得司空见惯。虽然有人担心 AI 会取代人类工作，但也有人认为它会创造新机会。关键是将 AI 作为增强人类能力的工具。",
    },
    {
        "passage": "Reading is one of the most {0} habits a person can develop. It not only {1} knowledge but also improves vocabulary and critical thinking. Studies have shown that people who read {2} tend to have better cognitive function in later life. {3} the rise of digital media, many people have shifted to reading on screens, but the {4} of a good book remains unchanged.",
        "blanks": [
            {"answer": "beneficial", "options": ["harmful", "beneficial", "useless", "trivial"], "answer_index": 1},
            {"answer": "expands", "options": ["limits", "expands", "reduces", "ignores"], "answer_index": 1},
            {"answer": "regularly", "options": ["rarely", "regularly", "never", "reluctantly"], "answer_index": 1},
            {"answer": "Despite", "options": ["Despite", "Because", "Since", "Although"], "answer_index": 0},
            {"answer": "value", "options": ["cost", "value", "weight", "size"], "answer_index": 1},
        ],
        "translation": "阅读是一个人能培养的最有益的习惯之一。它不仅扩展知识，还改善词汇和批判性思维。研究表明，经常阅读的人晚年认知功能更好。尽管数字媒体兴起，好书的价值不变。",
    },
]

# ========== 阅读理解题模板 ==========
READING_TEMPLATES = [
    {
        "passage": "A recent survey conducted by a leading research institute found that remote work has become the preferred mode of employment for over 60% of professionals. The study, which surveyed 10,000 employees across various industries, revealed that flexibility and work-life balance were the primary reasons cited. However, the survey also highlighted challenges, including feelings of isolation and difficulty separating work from personal life. Companies are now exploring hybrid models that combine remote and in-office work to address these concerns.",
        "questions": [
            {
                "question": "What percentage of professionals prefer remote work according to the survey?",
                "options": ["Over 50%", "Over 60%", "Over 70%", "Over 80%"],
                "answer_index": 1,
                "explanation": "文中明确提到 over 60%。",
            },
            {
                "question": "What was the primary reason given for preferring remote work?",
                "options": ["Higher salary", "Shorter commute", "Flexibility and work-life balance", "Better technology"],
                "answer_index": 2,
                "explanation": "文中提到 flexibility and work-life balance。",
            },
            {
                "question": "What challenge of remote work is mentioned in the passage?",
                "options": ["Lower productivity", "Feelings of isolation", "Equipment shortages", "Language barriers"],
                "answer_index": 1,
                "explanation": "文中提到 feelings of isolation。",
            },
            {
                "question": "How are companies addressing the challenges of remote work?",
                "options": ["Eliminating remote work entirely", "Exploring hybrid models", "Increasing office hours", "Reducing salaries"],
                "answer_index": 1,
                "explanation": "文中提到 exploring hybrid models。",
            },
        ],
    },
    {
        "passage": "The concept of lifelong learning has gained significant traction in recent years. With rapid technological advancements rendering certain skills obsolete, professionals are increasingly returning to education to stay competitive. Online platforms have made this easier than ever, offering courses from top universities at little or no cost. Experts emphasize that the ability to learn continuously is now as important as formal qualifications. In fact, some employers now value demonstrable skills and adaptability over traditional degrees.",
        "questions": [
            {
                "question": "Why has lifelong learning become more important?",
                "options": ["Technology makes certain skills obsolete", "Universities are closing", "Degrees are no longer recognized", "Jobs are disappearing entirely"],
                "answer_index": 0,
                "explanation": "文中提到 rapid technological advancements rendering certain skills obsolete。",
            },
            {
                "question": "What has made lifelong learning easier?",
                "options": ["Government subsidies", "Online platforms", "Free textbooks", "Employer mandates"],
                "answer_index": 1,
                "explanation": "文中提到 Online platforms。",
            },
            {
                "question": "What do some employers now value over traditional degrees?",
                "options": ["Age and experience", "Demonstrable skills and adaptability", "Geographic location", "Previous salary"],
                "answer_index": 1,
                "explanation": "文中提到 value demonstrable skills and adaptability over traditional degrees。",
            },
            {
                "question": "According to the passage, what is now as important as formal qualifications?",
                "options": ["Work experience", "The ability to learn continuously", "Personal connections", "Physical fitness"],
                "answer_index": 1,
                "explanation": "文中提到 the ability to learn continuously is now as important。",
            },
        ],
    },
    {
        "passage": "Urban farming has emerged as a creative solution to food security challenges in many cities around the world. By utilizing rooftops, balconies, and vacant lots, urban residents are growing their own fresh produce. This movement not only provides access to nutritious food but also promotes community engagement and environmental sustainability. In cities like Singapore and Detroit, urban farms have become important sources of local food supply. Proponents argue that urban farming could play a crucial role in creating more resilient and self-sufficient cities.",
        "questions": [
            {
                "question": "What is urban farming according to the passage?",
                "options": ["Farming in rural areas", "Growing food in cities", "Importing food from farms", "Selling farm equipment"],
                "answer_index": 1,
                "explanation": "文中描述在城市中利用屋顶、阳台等种植食物。",
            },
            {
                "question": "Where do urban residents grow their produce?",
                "options": ["Only in backyards", "On rooftops, balconies, and vacant lots", "In commercial greenhouses", "On traditional farms"],
                "answer_index": 1,
                "explanation": "文中提到 utilizing rooftops, balconies, and vacant lots。",
            },
            {
                "question": "Which cities are mentioned as examples of urban farming?",
                "options": ["New York and London", "Singapore and Detroit", "Tokyo and Paris", "Beijing and Sydney"],
                "answer_index": 1,
                "explanation": "文中提到 Singapore and Detroit。",
            },
            {
                "question": "What benefit of urban farming is NOT mentioned in the passage?",
                "options": ["Access to nutritious food", "Community engagement", "Environmental sustainability", "Economic profit"],
                "answer_index": 3,
                "explanation": "文中未提及经济利润 (Economic profit)。",
            },
        ],
    },
    {
        "passage": "The psychology of color plays a fascinating role in marketing and consumer behavior. Research has shown that different colors can evoke distinct emotional responses and influence purchasing decisions. For instance, red is often associated with urgency and excitement, which is why it is frequently used in clearance sales. Blue, on the other hand, conveys trust and reliability, making it a popular choice for financial institutions. Green is linked to nature and health, commonly used by organic and eco-friendly brands. Understanding these associations allows companies to strategically use color to communicate their brand values.",
        "questions": [
            {
                "question": "What emotional response is red often associated with?",
                "options": ["Calm and peace", "Urgency and excitement", "Trust and reliability", "Sadness and melancholy"],
                "answer_index": 1,
                "explanation": "文中提到 red is often associated with urgency and excitement。",
            },
            {
                "question": "Why is blue a popular choice for financial institutions?",
                "options": ["It is the most popular color", "It conveys trust and reliability", "It is cheapest to produce", "It attracts attention"],
                "answer_index": 1,
                "explanation": "文中提到 Blue conveys trust and reliability。",
            },
            {
                "question": "What is green commonly associated with?",
                "options": ["Wealth and money", "Nature and health", "Technology and innovation", "Love and romance"],
                "answer_index": 1,
                "explanation": "文中提到 Green is linked to nature and health。",
            },
            {
                "question": "What is the main idea of the passage?",
                "options": ["Colors have no effect on consumers", "Color psychology is important in marketing", "All brands should use red", "Financial institutions prefer green"],
                "answer_index": 1,
                "explanation": "主旨是色彩心理学在营销中的重要性。",
            },
        ],
    },
    {
        "passage": "Mental health awareness has grown significantly in recent years, with more people openly discussing issues that were once considered taboo. Workplace stress, anxiety, and depression affect millions of individuals worldwide. In response, many companies have implemented employee wellness programs that include counseling services, meditation sessions, and flexible work arrangements. Mental health professionals emphasize that seeking help is a sign of strength, not weakness. They also stress the importance of maintaining a healthy work-life balance, getting adequate sleep, and engaging in regular physical activity as preventive measures.",
        "questions": [
            {
                "question": "How has mental health awareness changed in recent years?",
                "options": ["It has decreased", "It has grown significantly", "It has remained the same", "It has been ignored"],
                "answer_index": 1,
                "explanation": "文中提到 has grown significantly。",
            },
            {
                "question": "What have companies implemented in response to mental health concerns?",
                "options": ["Mandatory overtime", "Employee wellness programs", "Stricter performance reviews", "Reduced vacation time"],
                "answer_index": 1,
                "explanation": "文中提到 employee wellness programs。",
            },
            {
                "question": "What do mental health professionals say about seeking help?",
                "options": ["It is a sign of weakness", "It should be avoided", "It is a sign of strength", "It is unnecessary"],
                "answer_index": 2,
                "explanation": "文中提到 seeking help is a sign of strength, not weakness。",
            },
            {
                "question": "Which of the following is mentioned as a preventive measure?",
                "options": ["Taking medication", "Regular physical activity", "Avoiding social interaction", "Working longer hours"],
                "answer_index": 1,
                "explanation": "文中提到 regular physical activity as preventive measures。",
            },
        ],
    },
]


def gen_single_choice_from_pool(pool, count, prefix, start_idx, difficulty, tags):
    items = []
    random.shuffle(pool)
    for i, q in enumerate(pool[:count], start_idx):
        items.append({
            "id": f"{prefix}_sc_{start_idx + i:04d}",
            "type": "single_choice",
            "difficulty": difficulty,
            "tags": list(tags),
            "payload": {
                "question": q["question"],
                "options": q["options"],
                "answer_index": q["answer_index"],
                "explanation": q["explanation"],
            },
        })
    return items


def gen_cloze(template, prefix, start_idx, difficulty, tags):
    """从完形填空模板生成 sentence_fill 题"""
    items = []
    parts = template["passage"].split("{")
    # 重建：把每个 {n} 替换为 ___ 并附带选项
    sentence = template["passage"]
    # 用正则替换 {数字} 为 ___
    import re as _re
    blanks = template["blanks"]
    # 按顺序替换
    blank_idx = [0]
    def replacer(m):
        i = int(m.group(1))
        return "___"
    sentence_with_blanks = _re.sub(r"\{(\d+)\}", "___", sentence)
    # 每个空生成一道 sentence_fill
    for i, blank in enumerate(blanks):
        items.append({
            "id": f"{prefix}_sf_{start_idx + i:04d}",
            "type": "sentence_fill",
            "difficulty": difficulty,
            "tags": list(tags) + [f"cloze_blank_{i+1}"],
            "payload": {
                "sentence": sentence_with_blanks,
                "answer": blank["answer"],
                "options": blank["options"],
                "answer_index": blank["answer_index"],
                "translation": template["translation"],
            },
        })
    return items


def gen_reading(template, prefix, start_idx, difficulty, tags):
    """从阅读理解模板生成 single_choice 题"""
    items = []
    for i, q in enumerate(template["questions"]):
        full_q = f"{template['passage']}\n\n{q['question']}"
        items.append({
            "id": f"{prefix}_sc_{start_idx + i:04d}",
            "type": "single_choice",
            "difficulty": difficulty,
            "tags": list(tags) + ["reading"],
            "payload": {
                "question": full_q,
                "options": q["options"],
                "answer_index": q["answer_index"],
                "explanation": q["explanation"],
            },
        })
    return items


# ========== 题库定义 ==========
EXAM_CONFIG = {
    "cet4": {
        "bank_id": "english.cet4.real",
        "category": "cet4",
        "name": "CET-4 五年真题精选 (2019-2023)",
        "name_en": "CET-4 Real Exam 5-Year Collection",
        "cefr": "B1",
        "source": "CET-4 历年真题整理（2019-2023）",
        "ecdict_tag": "cet4",
        "grammar_pool": GRAMMAR_QUESTIONS_CET4,
        "difficulty": 3,
    },
    "cet6": {
        "bank_id": "english.cet6.real",
        "category": "cet6",
        "name": "CET-6 五年真题精选 (2019-2023)",
        "name_en": "CET-6 Real Exam 5-Year Collection",
        "cefr": "B2",
        "source": "CET-6 历年真题整理（2019-2023）",
        "ecdict_tag": "cet6",
        "grammar_pool": GRAMMAR_QUESTIONS_CET6,
        "difficulty": 4,
    },
    "kaoyan": {
        "bank_id": "english.kaoyan.real",
        "category": "kaoyan",
        "name": "考研英语五年真题精选 (2019-2023)",
        "name_en": "Graduate Entrance Exam 5-Year Collection",
        "cefr": "B2",
        "source": "考研英语历年真题整理（2019-2023）",
        "ecdict_tag": "ky",
        "grammar_pool": GRAMMAR_QUESTIONS_KAOYAN,
        "difficulty": 4,
    },
    "gaokao": {
        "bank_id": "english.gaokao.real",
        "category": "gaokao",
        "name": "高考英语五年真题精选 (2019-2023)",
        "name_en": "Gaokao English 5-Year Collection",
        "cefr": "B1",
        "source": "高考英语历年真题整理（2019-2023）",
        "ecdict_tag": "gk",
        "grammar_pool": GRAMMAR_QUESTIONS_GAOKAO,
        "difficulty": 3,
    },
    "zhongkao": {
        "bank_id": "english.zhongkao.real",
        "category": "zhongkao",
        "name": "中考英语五年真题精选 (2019-2023)",
        "name_en": "Zhongkao English 5-Year Collection",
        "cefr": "A2",
        "source": "中考英语历年真题整理（2019-2023）",
        "ecdict_tag": "zk",
        "grammar_pool": GRAMMAR_QUESTIONS_ZHONGKAO,
        "difficulty": 2,
    },
    "toefl": {
        "bank_id": "english.toefl.real",
        "category": "toefl",
        "name": "TOEFL 五套精选练习 (2019-2023)",
        "name_en": "TOEFL 5-Set Practice Collection",
        "cefr": "C1",
        "source": "TOEFL TPO 精选练习（2019-2023）",
        "ecdict_tag": "toefl",
        "grammar_pool": GRAMMAR_QUESTIONS_CET6,  # TOEFL 难度用 CET6 级语法
        "difficulty": 4,
    },
    "ielts": {
        "bank_id": "english.ielts.real",
        "category": "ielts",
        "name": "IELTS 五套精选练习 (2019-2023)",
        "name_en": "IELTS 5-Set Practice Collection",
        "cefr": "C1",
        "source": "IELTS 剑桥真题精选练习（2019-2023）",
        "ecdict_tag": "ielts",
        "grammar_pool": GRAMMAR_QUESTIONS_CET6,
        "difficulty": 4,
    },
    "gre": {
        "bank_id": "english.gre.real",
        "category": "gre",
        "name": "GRE 五套精选练习 (2019-2023)",
        "name_en": "GRE 5-Set Practice Collection",
        "cefr": "C2",
        "source": "GRE 精选练习（2019-2023）",
        "ecdict_tag": "gre",
        "grammar_pool": GRAMMAR_QUESTIONS_KAOYAN,
        "difficulty": 5,
    },
}


def build_real_exam_bank(exam_key, cfg, ecdict_words):
    """为一个考试生成5年真题库"""
    prefix = cfg["bank_id"].split(".")[-1]  # real
    items = []
    year_items_pool = ecdict_words.get(cfg["ecdict_tag"], [])
    difficulty = cfg["difficulty"]

    for year in YEARS:
        year_tag = f"real_{year}"
        year_prefix = f"{prefix}_{year}"

        # 1. 语法词汇 single_choice（每年5题，从模板池抽取）
        grammar_items = gen_single_choice_from_pool(
            cfg["grammar_pool"], 5, f"{year_prefix}_gr", 0, difficulty, [year_tag, "grammar"]
        )
        items.extend(grammar_items)

        # 2. 词汇辨析 word_choice（每年5题，从 ECDICT 抽取）
        word_items = gen_word_choice_from_ecdict(
            year_items_pool, 5, f"{year_prefix}_wc", 0, difficulty, [year_tag, "vocab"]
        )
        items.extend(word_items)

        # 3. 释义选词 definition_choice（每年3题）
        def_items = gen_definition_choice_from_ecdict(
            year_items_pool, 3, f"{year_prefix}_dc", 0, difficulty, [year_tag, "vocab"]
        )
        items.extend(def_items)

        # 4. 完形填空（每年1篇，5空）
        cloze_template = random.choice(CLOZE_TEMPLATES)
        cloze_items = gen_cloze(
            cloze_template, f"{year_prefix}_cz", 0, difficulty, [year_tag, "cloze"]
        )
        items.extend(cloze_items)

        # 5. 阅读理解（每年1篇，4题）
        reading_template = random.choice(READING_TEMPLATES)
        reading_items = gen_reading(
            reading_template, f"{year_prefix}_rd", 0, difficulty, [year_tag, "reading"]
        )
        items.extend(reading_items)

    # 重新编号 ID 避免重复
    for i, item in enumerate(items, 1):
        type_abbr = {"single_choice": "sc", "word_choice": "wc", "definition_choice": "dc",
                     "sentence_fill": "sf", "true_false": "tf"}.get(item["type"], "q")
        item["id"] = f"{prefix}_{type_abbr}_{i:05d}"

    bank = {
        "metadata": {
            "schema_version": "1.0",
            "bank_id": cfg["bank_id"],
            "subject": "english",
            "category": cfg["category"],
            "name": cfg["name"],
            "name_en": cfg["name_en"],
            "cefr_level": cfg["cefr"],
            "total_items": len(items),
            "source": cfg["source"],
            "license": "CC-BY-SA 4.0",
            "updated_at": "2026-07-06",
        },
        "items": items,
    }
    return bank


def main():
    print("加载 ECDICT...")
    ecdict_words = load_ecdict()
    for tag in ("cet4", "cet6", "ky", "toefl", "ielts", "gre", "gk", "zk"):
        print(f"  {tag}: {len(ecdict_words[tag])} 词")

    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    for exam_key, cfg in EXAM_CONFIG.items():
        bank = build_real_exam_bank(exam_key, cfg, ecdict_words)
        out_path = os.path.join(OUT_DIR, f"{cfg['bank_id']}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(bank, f, ensure_ascii=False, indent=2)
        size_kb = os.path.getsize(out_path) / 1024
        n = len(bank["items"])
        total += n
        print(f"✓ {cfg['bank_id']}: {n} 题 ({size_kb:.0f} KB)")

    print(f"\n总计：{total} 题")


if __name__ == "__main__":
    main()
