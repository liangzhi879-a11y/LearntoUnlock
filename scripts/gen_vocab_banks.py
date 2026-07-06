#!/usr/bin/env python3
"""
从 ECDICT 批量生成英语题库 JSON。

策略：
- 每个考试 tag（cet4/cet6/ky/toefl/ielts/gre/gk/zk）的词生成 word_choice 题
- 词频前 30% 的词额外生成 definition_choice 题
- 干扰项从同词库随机抽取
- 难度按 BNC 词频分级：frq 越小难度越高
- 跳过无中文释义/单词过短/含特殊字符的词

输出：/workspace/quizbank/banks/english.<tag>.vocab.json
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

# 考试 tag → (bank_id, category, name, name_en, cefr, target_count)
# target_count = 官方推荐词汇量
EXAMS = {
    "cet4": {
        "bank_id": "english.cet4.vocab",
        "category": "cet4",
        "name": "CET-4 完整词汇",
        "name_en": "CET-4 Complete Vocabulary",
        "cefr": "B1",
        "source": "ECDICT (cet4 tag) + 官方大纲",
    },
    "cet6": {
        "bank_id": "english.cet6.vocab",
        "category": "cet6",
        "name": "CET-6 完整词汇",
        "name_en": "CET-6 Complete Vocabulary",
        "cefr": "B2",
        "source": "ECDICT (cet6 tag) + 官方大纲",
    },
    "ky": {
        "bank_id": "english.kaoyan.vocab",
        "category": "kaoyan",
        "name": "考研英语完整词汇",
        "name_en": "Graduate Entrance Exam Complete Vocabulary",
        "cefr": "B2",
        "source": "ECDICT (ky tag) + 考研大纲",
    },
    "toefl": {
        "bank_id": "english.toefl.vocab",
        "category": "toefl",
        "name": "托福完整词汇",
        "name_en": "TOEFL Complete Vocabulary",
        "cefr": "C1",
        "source": "ECDICT (toefl tag) + 托福核心词表",
    },
    "ielts": {
        "bank_id": "english.ielts.vocab",
        "category": "ielts",
        "name": "雅思完整词汇",
        "name_en": "IELTS Complete Vocabulary",
        "cefr": "C1",
        "source": "ECDICT (ielts tag) + 雅思核心词表",
    },
    "gre": {
        "bank_id": "english.gre.vocab",
        "category": "gre",
        "name": "GRE 完整词汇",
        "name_en": "GRE Complete Vocabulary",
        "cefr": "C2",
        "source": "ECDICT (gre tag) + GRE 核心词表",
    },
    "gk": {
        "bank_id": "english.gaokao.vocab",
        "category": "gaokao",
        "name": "高考英语完整词汇",
        "name_en": "Gaokao Complete Vocabulary",
        "cefr": "B1",
        "source": "ECDICT (gk tag) + 高考考纲",
    },
    "zk": {
        "bank_id": "english.zhongkao.vocab",
        "category": "zhongkao",
        "name": "中考英语完整词汇",
        "name_en": "Zhongkao Complete Vocabulary",
        "cefr": "A2",
        "source": "ECDICT (zk tag) + 中考考纲",
    },
}


def clean_word(w):
    """过滤不合适的词"""
    if not w:
        return False
    if len(w) < 2 or len(w) > 20:
        return False
    if not re.match(r"^[a-zA-Z][a-zA-Z\-'\s]*$", w):
        return False
    # 跳过含连字符/撇号过多的复合词
    if w.count("-") > 1 or w.count("'") > 1:
        return False
    return True


def parse_translation(trans):
    """从 translation 字段提取中文释义，取第一个意思"""
    if not trans:
        return None
    # 去掉词性标注 n./v./adj. 等
    parts = trans.replace("\n", "；").split("；")
    # 取前 3 个意思
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
    if ph.startswith("/"):
        return ph
    return f"/{ph}/" if ph else None


def _drop_none(d):
    """过滤掉值为 None 的字段（JSON Schema 可选字段为 null 会校验失败）"""
    return {k: v for k, v in d.items() if v is not None}


def make_word_choice(word, phonetic, pos, meanings, options, answer_idx, idx, prefix):
    """生成 word_choice 题"""
    return {
        "id": f"{prefix}_wc_{idx:05d}",
        "type": "word_choice",
        "difficulty": 3,
        "tags": ["vocab"],
        "payload": _drop_none({
            "word": word,
            "phonetic": phonetic,
            "pos": pos,
            "definitions": meanings,
            "options": options,
            "answer_index": answer_idx,
        }),
    }


def make_definition_choice(word, phonetic, meanings, options, answer_idx, idx, prefix):
    """生成 definition_choice 题"""
    return {
        "id": f"{prefix}_dc_{idx:05d}",
        "type": "definition_choice",
        "difficulty": 3,
        "tags": ["vocab"],
        "payload": _drop_none({
            "definition": meanings[0],
            "phonetic_hint": phonetic,
            "options": options,
            "answer_index": answer_idx,
        }),
    }


def build_bank(tag, cfg, words):
    """为一个考试 tag 生成题库"""
    prefix = cfg["bank_id"].split(".")[-1]  # vocab 前用 category
    items = []
    # 按词频排序，frq 小的（高频）在前
    words_sorted = sorted(words, key=lambda x: x.get("frq") or 999999)

    # 全部词生成 word_choice
    all_words_pool = [w["word"] for w in words_sorted]
    for i, w in enumerate(words_sorted, 1):
        meanings = parse_translation(w.get("translation", ""))
        if not meanings:
            continue
        # 干扰项：随机抽 3 个其他词的释义
        distractors = []
        attempts = 0
        while len(distractors) < 3 and attempts < 20:
            cand = random.choice(words_sorted)
            m = parse_translation(cand.get("translation", ""))
            if m and m[0] not in meanings and m[0] not in distractors:
                distractors.append(m[0])
            attempts += 1
        if len(distractors) < 3:
            continue
        options = meanings[:1] + distractors
        random.shuffle(options)
        answer_idx = options.index(meanings[0])
        items.append(
            make_word_choice(
                w["word"],
                parse_phonetic(w.get("phonetic")),
                w.get("pos") or None,
                meanings,
                options,
                answer_idx,
                i,
                prefix,
            )
        )

    # 词频前 30% 的词额外生成 definition_choice（给中文选英文）
    top30_count = len(words_sorted) // 3
    for i, w in enumerate(words_sorted[:top30_count], 1):
        meanings = parse_translation(w.get("translation", ""))
        if not meanings:
            continue
        # 干扰项：随机抽 3 个其他英文单词
        distractors = []
        attempts = 0
        while len(distractors) < 3 and attempts < 20:
            cand = random.choice(words_sorted)
            if cand["word"] != w["word"] and cand["word"] not in distractors:
                distractors.append(cand["word"])
            attempts += 1
        if len(distractors) < 3:
            continue
        options = [w["word"]] + distractors
        random.shuffle(options)
        answer_idx = options.index(w["word"])
        items.append(
            make_definition_choice(
                w["word"],
                parse_phonetic(w.get("phonetic")),
                meanings,
                options,
                answer_idx,
                i,
                prefix,
            )
        )

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
            "license": "ECDICT MIT + CC-BY-SA 4.0",
            "updated_at": "2026-07-06",
        },
        "items": items,
    }
    return bank


def main():
    # 按tag分组读取
    by_tag = defaultdict(list)
    with open(ECDICT_PATH, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            tag = row.get("tag", "")
            if not tag:
                continue
            word = row.get("word", "").strip()
            if not clean_word(word):
                continue
            trans = row.get("translation", "").strip()
            if not trans:
                continue
            for t in tag.split():
                if t in EXAMS:
                    by_tag[t].append(row)  # 一个词可属于多个考试 tag（ECDICT 独立标记）

    print("各 tag 词数：")
    for t in EXAMS:
        print(f"  {t}: {len(by_tag[t])}")

    os.makedirs(OUT_DIR, exist_ok=True)
    for tag, cfg in EXAMS.items():
        words = by_tag[tag]
        if not words:
            print(f"⚠ {tag} 无词，跳过")
            continue
        bank = build_bank(tag, cfg, words)
        out_path = os.path.join(OUT_DIR, f"{cfg['bank_id']}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(bank, f, ensure_ascii=False, indent=2)
        size_kb = os.path.getsize(out_path) / 1024
        print(f"✓ {cfg['bank_id']}: {len(bank['items'])} 题 ({size_kb:.0f} KB)")


if __name__ == "__main__":
    main()
