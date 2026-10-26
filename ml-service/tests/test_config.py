import pytest

from app.config import Settings

ENV_NAMES = [
    "DB_HOST",
    "DB_PORT",
    "DB_NAME",
    "DB_USERNAME",
    "MYSQL_ROOT_PASSWORD",
    "RECOMPUTE_WORKER_ENABLED",
    "RECOMPUTE_POLL_SECONDS",
    "RECOMPUTE_BATCH_SIZE",
]


@pytest.fixture
def clear_env(monkeypatch):
    for name in ENV_NAMES:
        monkeypatch.delenv(name, raising=False)
    return monkeypatch


def test_defaults_match_local_dev(clear_env):
    settings = Settings.from_env()
    assert settings == Settings()
    assert settings.db_host == "localhost"
    assert settings.db_password == "changeme_local_dev"
    assert settings.worker_enabled is True
    assert settings.poll_interval_seconds == 2.0


def test_reads_environment(clear_env):
    clear_env.setenv("DB_HOST", "mysql")
    clear_env.setenv("DB_PORT", "3307")
    clear_env.setenv("DB_NAME", "shop")
    clear_env.setenv("DB_USERNAME", "ml")
    clear_env.setenv("MYSQL_ROOT_PASSWORD", "test-password")
    clear_env.setenv("RECOMPUTE_WORKER_ENABLED", "false")
    clear_env.setenv("RECOMPUTE_POLL_SECONDS", "0.5")
    clear_env.setenv("RECOMPUTE_BATCH_SIZE", "10")
    settings = Settings.from_env()
    assert (settings.db_host, settings.db_port, settings.db_name) == ("mysql", 3307, "shop")
    assert (settings.db_user, settings.db_password) == ("ml", "test-password")
    assert settings.worker_enabled is False
    assert settings.poll_interval_seconds == 0.5
    assert settings.batch_size == 10


@pytest.mark.parametrize(
    ("value", "expected"),
    [("1", True), ("ON", True), ("yes", True), ("0", False), ("off", False), ("", True)],
)
def test_worker_enabled_flag(clear_env, value, expected):
    clear_env.setenv("RECOMPUTE_WORKER_ENABLED", value)
    assert Settings.from_env().worker_enabled is expected
