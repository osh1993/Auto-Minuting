"""
Auto Minuting 사용자 매뉴얼 PDF 생성 스크립트
fpdf2 라이브러리 사용, 맑은 고딕 한국어 폰트 적용
"""

from fpdf import FPDF
import re

FONT_REGULAR = "C:/Windows/Fonts/malgun.ttf"
FONT_BOLD    = "C:/Windows/Fonts/malgunbd.ttf"

# ─────────────────────────────────────────────
# 색상 팔레트
# ─────────────────────────────────────────────
C_PRIMARY   = (33, 150, 243)   # 파란색 — 제목/강조
C_ACCENT    = (21,  101, 192)  # 진파랑 — h2 배경
C_LIGHT_BG  = (232, 244, 253)  # 연파랑 — 코드/인용 배경
C_BORDER    = (187, 222, 251)  # 테두리
C_TEXT      = (33,  33,  33)   # 본문
C_MUTED     = (117, 117, 117)  # 보조 텍스트
C_WHITE     = (255, 255, 255)
C_TABLE_H   = (33, 150, 243)   # 테이블 헤더
C_TABLE_ROW = (245, 245, 245)  # 테이블 짝수 행


class ManualPDF(FPDF):
    def __init__(self, title=""):
        super().__init__()
        self.doc_title = title
        self.add_font("Malgun",  "", FONT_REGULAR, uni=True)
        self.add_font("Malgun",  "B", FONT_BOLD,    uni=True)
        self.set_margins(20, 20, 20)
        self.set_auto_page_break(auto=True, margin=20)

    # ── 헤더 / 푸터 ──────────────────────────
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Malgun", "B", 8)
        self.set_text_color(*C_MUTED)
        self.cell(0, 8, self.doc_title, align="L")
        self.set_draw_color(*C_BORDER)
        self.set_line_width(0.3)
        self.line(20, self.get_y() + 2, 190, self.get_y() + 2)
        self.ln(5)

    def footer(self):
        self.set_y(-15)
        self.set_font("Malgun", "", 8)
        self.set_text_color(*C_MUTED)
        self.cell(0, 10, f"- {self.page_no()} -", align="C")

    # ── 표지 ─────────────────────────────────
    def cover_page(self, title, subtitle, version, date):
        self.add_page()
        # 상단 컬러 바
        self.set_fill_color(*C_PRIMARY)
        self.rect(0, 0, 210, 60, "F")
        # 앱 이름
        self.set_xy(20, 15)
        self.set_font("Malgun", "B", 28)
        self.set_text_color(*C_WHITE)
        self.cell(0, 12, "Auto Minuting", ln=True)
        self.set_x(20)
        self.set_font("Malgun", "", 14)
        self.cell(0, 8, title, ln=True)

        # 본문 영역
        self.set_text_color(*C_TEXT)
        self.ln(20)
        self.set_x(20)
        self.set_font("Malgun", "B", 13)
        self.cell(0, 8, subtitle, ln=True)

        self.ln(10)
        info = [("버전", version), ("대상 기기", "삼성 갤럭시 스마트폰 (Android 12 이상)"), ("최종 수정", date)]
        for label, value in info:
            self.set_x(20)
            self.set_font("Malgun", "B", 10)
            self.set_text_color(*C_MUTED)
            self.cell(35, 7, label)
            self.set_font("Malgun", "", 10)
            self.set_text_color(*C_TEXT)
            self.cell(0, 7, value, ln=True)

    # ── H1 (문서 제목 수준) ───────────────────
    def h1(self, text):
        self.ln(6)
        self.set_fill_color(*C_PRIMARY)
        self.set_text_color(*C_WHITE)
        self.set_font("Malgun", "B", 14)
        self.set_x(20)
        self.cell(0, 10, text, fill=True, ln=True)
        self.ln(3)

    # ── H2 ───────────────────────────────────
    def h2(self, text):
        self.ln(5)
        self.set_fill_color(*C_ACCENT)
        self.set_text_color(*C_WHITE)
        self.set_font("Malgun", "B", 12)
        self.set_x(20)
        self.cell(0, 8, "  " + text, fill=True, ln=True)
        self.ln(2)
        self.set_text_color(*C_TEXT)

    # ── H3 ───────────────────────────────────
    def h3(self, text):
        self.ln(3)
        self.set_text_color(*C_PRIMARY)
        self.set_font("Malgun", "B", 11)
        self.set_x(20)
        # 왼쪽 색상 막대
        self.set_fill_color(*C_PRIMARY)
        self.rect(20, self.get_y(), 2, 6, "F")
        self.set_x(24)
        self.cell(0, 6, text, ln=True)
        self.ln(1)
        self.set_text_color(*C_TEXT)

    # ── 본문 단락 ─────────────────────────────
    def para(self, text, indent=0):
        self.set_font("Malgun", "", 10)
        self.set_text_color(*C_TEXT)
        self.set_x(20 + indent)
        self.multi_cell(170 - indent, 6, text)
        self.ln(1)

    # ── 굵은 인라인이 섞인 단락 ──────────────
    def para_mixed(self, text, indent=0):
        """**bold** 구문을 처리하여 굵게 출력"""
        parts = re.split(r'(\*\*[^*]+\*\*)', text)
        self.set_x(20 + indent)
        x_start = self.get_x()
        y_start = self.get_y()
        line_h = 6
        available_w = 170 - indent
        x = x_start
        y = y_start

        for part in parts:
            if part.startswith("**") and part.endswith("**"):
                content = part[2:-2]
                self.set_font("Malgun", "B", 10)
            else:
                content = part
                self.set_font("Malgun", "", 10)

            if not content:
                continue

            words = content.split(" ")
            for i, word in enumerate(words):
                if not word:
                    continue
                w = self.get_string_width(word + " ")
                if x + w > x_start + available_w and x > x_start:
                    x = x_start
                    y += line_h
                self.set_xy(x, y)
                self.cell(w, line_h, word + " ")
                x += w

        self.set_xy(20, y + line_h)
        self.ln(1)

    # ── 번호 없는 목록 항목 ───────────────────
    def bullet(self, text, level=0):
        indent = level * 8
        bullet_char = "•" if level == 0 else "◦"
        self.set_font("Malgun", "", 10)
        self.set_text_color(*C_TEXT)
        self.set_x(22 + indent)
        self.cell(5, 6, bullet_char)
        self.set_x(27 + indent)
        # **bold** 처리
        clean = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
        self.multi_cell(163 - indent, 6, clean)

    # ── 번호 있는 목록 항목 ───────────────────
    def numbered(self, num, text, indent=0):
        self.set_font("Malgun", "", 10)
        self.set_text_color(*C_TEXT)
        self.set_x(22 + indent)
        self.cell(8, 6, f"{num}.")
        self.set_x(30 + indent)
        clean = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
        self.multi_cell(160 - indent, 6, clean)

    # ── 코드 블록 ─────────────────────────────
    def code_block(self, lines):
        self.ln(2)
        self.set_fill_color(*C_LIGHT_BG)
        self.set_draw_color(*C_BORDER)
        self.set_line_width(0.3)
        content = "\n".join(lines)
        self.set_font("Malgun", "", 9)
        self.set_text_color(60, 60, 60)
        self.set_x(20)
        self.multi_cell(170, 5.5, content, border=1, fill=True)
        self.ln(2)
        self.set_text_color(*C_TEXT)

    # ── 인용/경고 블록 ────────────────────────
    def blockquote(self, text):
        self.ln(2)
        self.set_fill_color(*C_LIGHT_BG)
        self.set_draw_color(*C_PRIMARY)
        self.set_line_width(1)
        y = self.get_y()
        self.line(22, y, 22, y + 12)
        self.set_line_width(0.3)
        self.set_font("Malgun", "", 9)
        self.set_text_color(*C_MUTED)
        self.set_x(26)
        clean = re.sub(r'\*\*([^*]+)\*\*', r'\1', text.lstrip("> "))
        self.multi_cell(164, 6, clean, fill=True)
        self.ln(2)
        self.set_text_color(*C_TEXT)

    # ── 구분선 ────────────────────────────────
    def divider(self):
        self.ln(3)
        self.set_draw_color(*C_BORDER)
        self.set_line_width(0.5)
        self.line(20, self.get_y(), 190, self.get_y())
        self.ln(4)

    # ── 테이블 ────────────────────────────────
    def table(self, headers, rows, col_widths=None):
        n = len(headers)
        if col_widths is None:
            col_widths = [170 // n] * n

        # 헤더
        self.set_fill_color(*C_TABLE_H)
        self.set_text_color(*C_WHITE)
        self.set_font("Malgun", "B", 9)
        self.set_x(20)
        for i, h in enumerate(headers):
            self.cell(col_widths[i], 7, h, border=1, fill=True)
        self.ln()

        # 행
        self.set_text_color(*C_TEXT)
        self.set_font("Malgun", "", 9)
        for row_idx, row in enumerate(rows):
            if row_idx % 2 == 1:
                self.set_fill_color(*C_TABLE_ROW)
                fill = True
            else:
                fill = False
            self.set_x(20)
            # 가장 높은 셀 높이 계산
            max_lines = 1
            for i, cell in enumerate(row):
                lines = self.get_string_width(cell) / (col_widths[i] - 2)
                max_lines = max(max_lines, int(lines) + 1)
            cell_h = max(6, max_lines * 5)
            for i, cell in enumerate(row):
                self.multi_cell(col_widths[i], cell_h, cell, border=1,
                                fill=fill, max_line_height=5)
                # multi_cell은 줄바꿈하므로 위치 재조정
                # 단순 처리: 한 줄로 강제 (긴 내용은 잘릴 수 있음)
            # 위 multi_cell 방식 대신 cell로 처리
            self.set_x(20)
            for i, cell in enumerate(row):
                self.cell(col_widths[i], cell_h, cell, border=1, fill=fill)
            self.ln()
        self.ln(2)


# ─────────────────────────────────────────────
# Markdown 파싱 및 렌더링
# ─────────────────────────────────────────────

def sanitize(text: str) -> str:
    """맑은 고딕에서 렌더링 불가한 특수문자를 대체"""
    return text.replace("\u22ee", "[...]").replace("\u2713", "V").replace("\u25e6", "o")


def render_md(pdf: ManualPDF, md_text: str):
    md_text = sanitize(md_text)
    lines = md_text.splitlines()
    i = 0
    list_counter = {}  # indent → 번호 카운터

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # 코드 블록
        if stripped.startswith("```"):
            i += 1
            code_lines = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code_lines.append(lines[i])
                i += 1
            pdf.code_block(code_lines)
            i += 1
            continue

        # 구분선
        if stripped in ("---", "***", "___"):
            pdf.divider()
            i += 1
            continue

        # 제목
        if stripped.startswith("#### "):
            pdf.h3(stripped[5:])
        elif stripped.startswith("### "):
            pdf.h3(stripped[4:])
        elif stripped.startswith("## "):
            pdf.h2(stripped[3:])
        elif stripped.startswith("# "):
            pdf.h1(stripped[2:])

        # 인용
        elif stripped.startswith("> "):
            pdf.blockquote(stripped)

        # 테이블
        elif stripped.startswith("|") and "|" in stripped:
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i].strip())
                i += 1
            # 헤더 / 구분선 / 데이터 파싱
            if len(table_lines) >= 2:
                headers = [c.strip() for c in table_lines[0].split("|") if c.strip()]
                rows = []
                for tl in table_lines[2:]:
                    row = [c.strip() for c in tl.split("|") if c.strip()]
                    if row:
                        rows.append(row)
                # 컬럼 너비 균등 배분
                n = len(headers)
                if n > 0:
                    w = 170 // n
                    pdf.table(headers, rows, [w] * n)
            continue

        # 번호 있는 목록
        elif re.match(r'^\d+\. ', stripped):
            m = re.match(r'^(\d+)\. (.+)', stripped)
            if m:
                indent = len(line) - len(line.lstrip())
                pdf.numbered(int(m.group(1)), m.group(2), indent=indent // 2 * 4)

        # 번호 없는 목록
        elif stripped.startswith("- ") or stripped.startswith("* "):
            indent = len(line) - len(line.lstrip())
            level = indent // 2
            text = stripped[2:]
            pdf.bullet(text, level=level)

        # 빈 줄
        elif stripped == "":
            pdf.ln(2)

        # 일반 단락 (굵은 텍스트 포함)
        else:
            # 전체가 **text** 형태 (강조 단락)인지 확인
            if stripped.startswith("**") and stripped.endswith("**") and stripped.count("**") == 2:
                pdf.set_font("Malgun", "B", 10)
                pdf.set_text_color(*C_TEXT)
                pdf.set_x(20)
                pdf.multi_cell(170, 6, stripped[2:-2])
                pdf.ln(1)
                pdf.set_text_color(*C_TEXT)
            elif "**" in stripped:
                pdf.para_mixed(stripped)
            else:
                # 이탤릭 *text* 제거
                clean = re.sub(r'\*([^*]+)\*', r'\1', stripped)
                # 인라인 코드 `text`
                clean = re.sub(r'`([^`]+)`', r'[\1]', clean)
                # 링크 [text](url)
                clean = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', clean)
                pdf.para(clean)

        i += 1


# ─────────────────────────────────────────────
# 실행
# ─────────────────────────────────────────────

def build_manual():
    with open("MANUAL.md", encoding="utf-8") as f:
        manual_md = f.read()

    pdf = ManualPDF(title="Auto Minuting 상세 사용자 매뉴얼")
    pdf.cover_page(
        title="상세 사용자 매뉴얼",
        subtitle="설치부터 회의록 생성까지 전체 가이드",
        version="v5.0",
        date="2026-03-31"
    )
    pdf.add_page()
    render_md(pdf, manual_md)
    pdf.output("Auto_Minuting_Manual.pdf")
    print("Auto_Minuting_Manual.pdf created")


def build_quickstart():
    with open("QUICK_START.md", encoding="utf-8") as f:
        qs_md = f.read()

    pdf = ManualPDF(title="Auto Minuting 빠른 시작 가이드")
    pdf.cover_page(
        title="빠른 시작 가이드",
        subtitle="5분 안에 첫 회의록 만들기",
        version="v5.0",
        date="2026-03-31"
    )
    pdf.add_page()
    render_md(pdf, qs_md)
    pdf.output("Auto_Minuting_QuickStart.pdf")
    print("Auto_Minuting_QuickStart.pdf created")


if __name__ == "__main__":
    build_manual()
    build_quickstart()
