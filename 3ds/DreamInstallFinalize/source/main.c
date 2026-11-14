#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <3ds.h>
#include <citro2d.h>

#include "basetik_bin.h"

// DreamInstallFinalize
// Finishes the titles custom-install wrote to the SD: installs the ticket and the seed,
// then shows what was finalized. Based on custom-install-finalize by ihaveamac (MIT).

#define CIFINISH_PATH "/cifinish.bin"
#define NAMES_PATH "/dreamfinalize-names.txt"
#define FINALIZER_TITLE_ID 0x000400000FF4D100ULL

#define COLOR_BG      C2D_Color32(0x08, 0x06, 0x0D, 0xFF)
#define COLOR_CARD    C2D_Color32(0x15, 0x0E, 0x21, 0xFF)
#define COLOR_BORDER  C2D_Color32(0x2E, 0x23, 0x42, 0xFF)
#define COLOR_ACCENT  C2D_Color32(0xA8, 0x55, 0xF7, 0xFF)
#define COLOR_HIGHLIGHT C2D_Color32(0xCB, 0xB0, 0xF5, 0xFF)
#define COLOR_TEXT    C2D_Color32(0xF4, 0xEE, 0xFC, 0xFF)
#define COLOR_DIM     C2D_Color32(0x9E, 0x93, 0xB0, 0xFF)
#define COLOR_OK      C2D_Color32(0x54, 0xD0, 0x8A, 0xFF)
#define COLOR_WARN    C2D_Color32(0xE7, 0xB4, 0x5C, 0xFF)
#define COLOR_BAD     C2D_Color32(0xE4, 0x4C, 0x3E, 0xFF)

#define MAX_NAMES 512
#define MAX_ROWS 5

struct finish_db_header {
	u8 magic[8];
	u32 version;
	u32 title_count;
};

struct finish_db_entry_v1 {
	u64 title_id;
	u8 common_key_index;
	bool has_seed;
	u8 magic[6];
	u8 title_key[0x10];
	u8 seed[0x10];
};

struct finish_db_entry_v2 {
	u8 magic[6];
	u64 title_id;
	bool has_seed;
	u8 padding;
	u8 seed[0x10];
} __attribute__((packed));

struct finish_db_entry_v3 {
	u8 magic[6];
	bool has_seed;
	u64 title_id;
	u8 seed[0x10];
};

struct ticket_dumb {
	u8 unused1[0x1DC];
	u64 title_id_be;
	u8 unused2[0x16C];
} __attribute__((packed));

typedef struct {
	bool has_seed;
	u64 title_id;
	u8 seed[0x10];
} TitleEntry;

typedef struct {
	bool had_ticket;
	bool ticket_ok;
	bool seed_required;
	bool seed_ok;
	bool valid;
} TitleResult;

typedef struct {
	u64 id;
	char name[96];
} NameEntry;

static NameEntry g_names[MAX_NAMES];
static int g_name_count = 0;

static C2D_Font g_font;
static C2D_TextBuf g_text_buf;
static C3D_RenderTarget *g_top;
static C3D_RenderTarget *g_bottom;

static char g_message[256] = "";

// from FBI (same as custom-install-finalize)
static Result FSUSER_AddSeed(u64 titleId, const void *seed) {
	u32 *cmdbuf = getThreadCommandBuffer();
	cmdbuf[0] = 0x087A0180;
	cmdbuf[1] = (u32)(titleId & 0xFFFFFFFF);
	cmdbuf[2] = (u32)(titleId >> 32);
	memcpy(&cmdbuf[3], seed, 16);

	Result ret = 0;
	if (R_FAILED(ret = svcSendSyncRequest(*fsGetSessionHandle()))) return ret;
	return cmdbuf[1];
}

static void load_names(void) {
	g_name_count = 0;
	FILE *fp = fopen(NAMES_PATH, "rb");
	if (!fp) return;

	char line[256];
	while (fgets(line, sizeof(line), fp) && g_name_count < MAX_NAMES) {
		char *tab = strchr(line, '\t');
		if (!tab) continue;
		*tab = '\0';
		u64 id = strtoull(line, NULL, 16);
		char *name = tab + 1;
		size_t len = strcspn(name, "\r\n");
		name[len] = '\0';
		if (!name[0]) continue;
		g_names[g_name_count].id = id;
		snprintf(g_names[g_name_count].name, sizeof(g_names[g_name_count].name), "%s", name);
		g_name_count++;
	}
	fclose(fp);
}

static const char *find_name(u64 id) {
	for (int i = 0; i < g_name_count; i++) {
		if (g_names[i].id == id) return g_names[i].name;
	}
	return NULL;
}

static int load_cifinish(const char *path, TitleEntry **entries) {
	FILE *fp = fopen(path, "rb");
	if (!fp) {
		snprintf(g_message, sizeof(g_message), "cifinish.bin nao encontrado no cartao.");
		return -1;
	}

	struct finish_db_header header;
	if (fread(&header, sizeof(header), 1, fp) != 1) {
		fclose(fp);
		snprintf(g_message, sizeof(g_message), "cifinish.bin corrompido.");
		return -1;
	}
	if (memcmp(header.magic, "CIFINISH", 8)) {
		fclose(fp);
		snprintf(g_message, sizeof(g_message), "CIFINISH invalido.");
		return -1;
	}
	if (header.version < 1 || header.version > 3) {
		fclose(fp);
		snprintf(g_message, sizeof(g_message), "Versao de cifinish.bin nao suportada: %lu", (unsigned long)header.version);
		return -1;
	}
	if (header.title_count == 0) {
		fclose(fp);
		snprintf(g_message, sizeof(g_message), "Nenhum titulo para finalizar.");
		return 0;
	}

	TitleEntry *list = calloc(header.title_count, sizeof(TitleEntry));
	if (!list) {
		fclose(fp);
		snprintf(g_message, sizeof(g_message), "Sem memoria para %lu titulos.", (unsigned long)header.title_count);
		return -1;
	}

	for (u32 i = 0; i < header.title_count; i++) {
		if (header.version == 1) {
			struct finish_db_entry_v1 v1;
			if (fread(&v1, sizeof(v1), 1, fp) != 1 || memcmp(v1.magic, "TITLE", 6)) goto corrupt;
			list[i].has_seed = v1.has_seed;
			list[i].title_id = v1.title_id;
			memcpy(list[i].seed, v1.seed, 16);
		} else if (header.version == 2) {
			struct finish_db_entry_v2 v2;
			if (fread(&v2, sizeof(v2), 1, fp) != 1 || memcmp(v2.magic, "TITLE", 6)) goto corrupt;
			list[i].has_seed = v2.has_seed;
			list[i].title_id = v2.title_id;
			memcpy(list[i].seed, v2.seed, 16);
		} else {
			struct finish_db_entry_v3 v3;
			if (fread(&v3, sizeof(v3), 1, fp) != 1 || memcmp(v3.magic, "TITLE", 6)) goto corrupt;
			list[i].has_seed = v3.has_seed;
			list[i].title_id = v3.title_id;
			memcpy(list[i].seed, v3.seed, 16);
		}
	}

	fclose(fp);
	*entries = list;
	return (int)header.title_count;

corrupt:
	free(list);
	fclose(fp);
	snprintf(g_message, sizeof(g_message), "cifinish.bin truncado ou corrompido.");
	return -1;
}

typedef struct {
	u64 *titles;
	u32 title_count;
	u64 *tickets;
	u32 ticket_count;
} InstalledLists;

static bool list_contains(u64 *ids, u32 count, u64 id) {
	for (u32 i = 0; i < count; i++) {
		if (ids[i] == id) return true;
	}
	return false;
}

static void free_lists(InstalledLists *lists) {
	free(lists->titles);
	free(lists->tickets);
	lists->titles = NULL;
	lists->tickets = NULL;
}

static bool query_lists(InstalledLists *lists) {
	memset(lists, 0, sizeof(*lists));
	u32 title_count = 0, ticket_count = 0;

	if (R_FAILED(AM_GetTitleCount(MEDIATYPE_SD, &title_count))) return false;
	if (R_FAILED(AM_GetTicketCount(&ticket_count))) return false;

	lists->titles = title_count ? calloc(title_count, sizeof(u64)) : NULL;
	lists->tickets = ticket_count ? calloc(ticket_count, sizeof(u64)) : NULL;

	u32 read = title_count;
	if (title_count && R_FAILED(AM_GetTitleList(&read, MEDIATYPE_SD, title_count, lists->titles))) {
		free_lists(lists);
		return false;
	}
	lists->title_count = read;

	read = ticket_count;
	if (ticket_count && R_FAILED(AM_GetTicketList(&read, ticket_count, 0, lists->tickets))) {
		free_lists(lists);
		return false;
	}
	lists->ticket_count = read;
	return true;
}

static bool install_ticket(u64 title_id) {
	struct ticket_dumb ticket;
	memcpy(&ticket, basetik_bin, sizeof(ticket));
	ticket.title_id_be = __builtin_bswap64(title_id);

	Handle handle;
	Result res = AMNET_InstallTicketBegin(&handle);
	if (R_FAILED(res)) return false;
	if (R_FAILED(res = FSFILE_Write(handle, NULL, 0, &ticket, sizeof(ticket), 0))) {
		AMNET_InstallTicketAbort(handle);
		return false;
	}
	if (R_FAILED(res = AMNET_InstallTicketFinish(handle))) {
		AMNET_InstallTicketAbort(handle);
		return false;
	}
	return true;
}

static int finalize_titles(TitleEntry *entries, int count, TitleResult **results_out) {
	TitleResult *results = calloc(count, sizeof(TitleResult));
	if (!results) return -1;
	*results_out = results;

	InstalledLists before;
	if (!query_lists(&before)) {
		snprintf(g_message, sizeof(g_message), "Falha ao consultar os titulos instalados.");
		return -1;
	}

	for (int i = 0; i < count; i++) {
		u64 id = entries[i].title_id;
		results[i].seed_required = entries[i].has_seed;
		results[i].had_ticket = list_contains(before.tickets, before.ticket_count, id);

		if (results[i].had_ticket) {
			results[i].ticket_ok = true;
		} else {
			results[i].ticket_ok = install_ticket(id);
		}

		if (entries[i].has_seed) {
			results[i].seed_ok = R_SUCCEEDED(FSUSER_AddSeed(id, entries[i].seed));
		}
	}

	free_lists(&before);

	InstalledLists after;
	if (!query_lists(&after)) {
		snprintf(g_message, sizeof(g_message), "Falha ao validar os titulos finalizados.");
		return -1;
	}

	for (int i = 0; i < count; i++) {
		u64 id = entries[i].title_id;
		bool has_ticket = list_contains(after.tickets, after.ticket_count, id);
		bool has_title = list_contains(after.titles, after.title_count, id);
		results[i].valid = has_ticket && has_title;
	}
	free_lists(&after);

	return count;
}

static void draw_text(C2D_TextBuf buf, const char *text, float x, float y, float scale, u32 color) {
	C2D_Text t;
	C2D_TextFontParse(&t, g_font, buf, text);
	C2D_TextOptimize(&t);
	C2D_DrawText(&t, C2D_WithColor, x, y, 0.5f, scale, scale, color);
}

static void truncate_name(const char *name, char *out, size_t out_size, size_t max_chars) {
	size_t len = strlen(name);
	if (len <= max_chars) {
		snprintf(out, out_size, "%s", name);
		return;
	}
	size_t cut = max_chars > 3 ? max_chars - 3 : 0;
	if (cut >= out_size) cut = out_size - 1;
	memcpy(out, name, cut);
	out[cut] = '\0';
	strncat(out, "...", out_size - strlen(out) - 1);
}

static int count_status(TitleResult *results, TitleEntry *entries, int count, int status) {
	int n = 0;
	for (int i = 0; i < count; i++) {
		if (entries[i].title_id == FINALIZER_TITLE_ID) continue;
		int s;
		if (!results[i].ticket_ok || !results[i].valid) s = 3;
		else if (results[i].seed_required && !results[i].seed_ok) s = 2;
		else if (results[i].had_ticket) s = 1;
		else s = 0;
		if (s == status) n++;
	}
	return n;
}

static void draw_top(TitleEntry *entries, int count, TitleResult *results) {
	C2D_TargetClear(g_top, COLOR_BG);
	C2D_SceneBegin(g_top);

	// accent line
	C2D_DrawRectSolid(0, 0, 0.5f, 400, 3, COLOR_ACCENT);

	draw_text(g_text_buf, "DreamInstallFinalize", 16, 12, 0.82f, COLOR_TEXT);
	draw_text(g_text_buf, "Finalizador de instalacoes", 17, 38, 0.50f, COLOR_DIM);

	int visible[64];
	int visible_count = 0;
	for (int i = 0; i < count && visible_count < 64; i++) {
		if (entries[i].title_id != FINALIZER_TITLE_ID) visible[visible_count++] = i;
	}

	char line[128];
	if (visible_count > 0) {
		snprintf(line, sizeof(line), "%d instalado(s)   %d ja estava(m)   %d falhou/falharam",
		         count_status(results, entries, count, 0), count_status(results, entries, count, 1),
		         count_status(results, entries, count, 3));
		draw_text(g_text_buf, line, 16, 54, 0.48f, COLOR_DIM);
	} else {
		draw_text(g_text_buf, g_message[0] ? g_message : "Nenhum jogo para finalizar.", 16, 56, 0.52f, COLOR_WARN);
	}

	int rows = visible_count < MAX_ROWS ? visible_count : MAX_ROWS;
	for (int r = 0; r < rows; r++) {
		int i = visible[r];
		float y = 78 + r * 29;
		C2D_DrawRectSolid(14, y, 0.5f, 372, 25, COLOR_CARD);
		C2D_DrawRectSolid(14, y, 0.5f, 3, 25, COLOR_BORDER);

		u32 status_color;
		const char *status_text;
		if (!results[i].ticket_ok || !results[i].valid) {
			status_color = COLOR_BAD;
			status_text = "Falhou";
		} else if (results[i].seed_required && !results[i].seed_ok) {
			status_color = COLOR_WARN;
			status_text = "Seed falhou";
		} else if (results[i].had_ticket) {
			status_color = COLOR_DIM;
			status_text = "Ja estava";
		} else {
			status_color = COLOR_OK;
			status_text = "Finalizado";
		}
		C2D_DrawRectSolid(14, y, 0.5f, 3, 25, status_color);
		const char *name = find_name(entries[i].title_id);
		char display[80];
		char truncated[72];
		if (name) {
			truncate_name(name, truncated, sizeof(truncated), 34);
			snprintf(display, sizeof(display), "%s", truncated);
		} else {
			snprintf(display, sizeof(display), "%016llX", (unsigned long long)entries[i].title_id);
		}
		draw_text(g_text_buf, display, 24, y + 4, 0.52f, COLOR_TEXT);

		char id_text[32];
		snprintf(id_text, sizeof(id_text), "%016llX", (unsigned long long)entries[i].title_id);
		draw_text(g_text_buf, id_text, 246, y + 6, 0.36f, COLOR_DIM);
		draw_text(g_text_buf, status_text, 330, y + 6, 0.42f, status_color);
	}

	if (visible_count > MAX_ROWS) {
		snprintf(line, sizeof(line), "+%d outro(s) jogo(s)", visible_count - MAX_ROWS);
		draw_text(g_text_buf, line, 16, 78 + MAX_ROWS * 29, 0.45f, COLOR_DIM);
	}
}

static void draw_bottom(int mode) {
	C2D_TargetClear(g_bottom, COLOR_BG);
	C2D_SceneBegin(g_bottom);

	C2D_DrawRectSolid(0, 0, 0.5f, 320, 3, COLOR_ACCENT);
	draw_text(g_text_buf, "START", 121, 74, 0.95f, COLOR_ACCENT);
	draw_text(g_text_buf, "para sair", 128, 106, 0.55f, COLOR_TEXT);

	const char *note;
	if (mode == 2) {
		note = "Nada foi alterado no cartao.";
	} else if (mode == 1) {
		note = "cifinish.bin foi mantido; rode de novo para tentar os que falharam.";
	} else {
		note = "cifinish.bin foi removido. Pode voltar ao menu.";
	}
	draw_text(g_text_buf, note, 16, 158, 0.40f, COLOR_DIM);
	draw_text(g_text_buf, "github.com/eduhbarb/DreamInstaller", 16, 206, 0.34f, COLOR_DIM);
}

int main(int argc, char *argv[]) {
	(void)argc;
	(void)argv;

	amInit();
	fsInit();
	cfguInit();
	gfxInitDefault();
	C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
	C2D_Init(C2D_DEFAULT_MAX_OBJECTS);
	C2D_Prepare();

	g_top = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
	g_bottom = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);
	g_text_buf = C2D_TextBufNew(8192);
	g_font = C2D_FontLoadSystem(CFG_REGION_USA);

	load_names();

	TitleEntry *entries = NULL;
	TitleResult *results = NULL;
	int count = load_cifinish(CIFINISH_PATH, &entries);
	int finalized = 0;

	if (count > 0) {
		finalized = finalize_titles(entries, count, &results);
		if (finalized < 0) count = 0;
	}

	bool any_failed = false;
	if (finalized > 0) {
		for (int i = 0; i < finalized; i++) {
			if (!results[i].valid) any_failed = true;
		}
	}

	int bottom_mode = 2;
	if (finalized > 0 && !any_failed) {
		unlink(CIFINISH_PATH);
		unlink(NAMES_PATH);
		bottom_mode = 0;
	} else if (finalized > 0) {
		bottom_mode = 1;
	}

	while (aptMainLoop()) {
		hidScanInput();
		u32 kDown = hidKeysDown();
		if ((kDown & KEY_START) || (kDown & KEY_B)) break;

		C2D_TextBufClear(g_text_buf);

		C3D_FrameBegin(C3D_FRAME_SYNCDRAW);
		if (finalized > 0) {
			draw_top(entries, finalized, results);
		} else {
			C2D_TargetClear(g_top, COLOR_BG);
			C2D_SceneBegin(g_top);
			C2D_DrawRectSolid(0, 0, 0.5f, 400, 3, COLOR_ACCENT);
			draw_text(g_text_buf, "DreamInstallFinalize", 16, 12, 0.82f, COLOR_TEXT);
			draw_text(g_text_buf, g_message[0] ? g_message : "Nada para finalizar.", 24, 96, 0.55f, COLOR_TEXT);
		}
		draw_bottom(bottom_mode);
		C3D_FrameEnd(0);
	}

	if (results) free(results);
	if (entries) free(entries);

	C2D_TextBufDelete(g_text_buf);
	C2D_Fini();
	C3D_Fini();
	gfxExit();
	cfguExit();
	fsExit();
	amExit();
	return 0;
}
