
import json
import argparse
from pathlib import Path


def extract_ingredients(recipe):
    """提取配方中的原材料，支持 item、items 和 tag。"""
    predicates = []

    def parse_ingredient(value):
        if isinstance(value, list):
            for v in value:
                parse_ingredient(v)

        elif isinstance(value, str):
            if ":" in value:
                predicates.append({"items": [value]})

        elif isinstance(value, dict):
            if "item" in value:
                item = value["item"]
                if isinstance(item, str):
                    predicates.append({"items": [item]})

            elif "items" in value:
                items = value["items"]
                if isinstance(items, str):
                    items = [items]
                if isinstance(items, list) and items:
                    predicates.append({"items": items})

            elif "tag" in value:
                predicates.append({"tag": value["tag"]})

            else:
                for v in value.values():
                    parse_ingredient(v)

    # 只读取原材料字段，不读取 result 产物
    ingredient_keys = {
        "ingredients",
        "ingredient",
        "key",
        "template",
        "base",
        "addition",
        "input"
    }

    for key, value in recipe.items():
        if key in ingredient_keys:
            parse_ingredient(value)

    # 去重
    unique = []
    seen = set()

    for predicate in predicates:
        data = json.dumps(predicate, sort_keys=True)
        if data not in seen:
            seen.add(data)
            unique.append(predicate)

    return unique


def generate(recipes_dir, advancement_dir, function_dir, namespace):
    recipes_dir = Path(recipes_dir)
    advancement_dir = Path(advancement_dir)
    function_dir = Path(function_dir)

    if not recipes_dir.exists():
        print(f"错误：找不到配方文件夹：{recipes_dir}")
        return

    count = 0
    skipped = 0

    for recipe_file in recipes_dir.rglob("*.json"):
        try:
            with recipe_file.open("r", encoding="utf-8") as f:
                recipe = json.load(f)
        except Exception as e:
            print(f"[跳过] JSON读取失败：{recipe_file} ({e})")
            skipped += 1
            continue

        ingredients = extract_ingredients(recipe)

        if not ingredients:
            print(f"[跳过] 没有识别到原材料：{recipe_file}")
            skipped += 1
            continue

        relative_path = recipe_file.relative_to(recipes_dir).with_suffix("")
        recipe_id = f"{namespace}:{relative_path.as_posix()}"

        # 为每种原材料创建一个独立的触发条件
        criteria = {}

        for i, ingredient in enumerate(ingredients, 1):
            criteria[f"has_ingredient_{i:03d}"] = {
                "trigger": "minecraft:inventory_changed",
                "conditions": {
                    "items": [ingredient]
                }
            }

        # 任意一个条件满足即可解锁
        advancement = {
            "criteria": criteria,
            "requirements": [
                list(criteria.keys())
            ],
            "rewards": {
                "recipes": [recipe_id],
                "function": f"{namespace}:{relative_path.as_posix()}"
            }
        }

        # 生成 advancement
        advancement_file = advancement_dir / relative_path.with_suffix(".json")
        advancement_file.parent.mkdir(parents=True, exist_ok=True)

        with advancement_file.open("w", encoding="utf-8") as f:
            json.dump(advancement, f, ensure_ascii=False, indent=2)

        # 生成自动撤销 advancement 的函数
        # 撤销后再次获得材料仍然可以触发
        function_file = function_dir / relative_path.with_suffix(".mcfunction")
        function_file.parent.mkdir(parents=True, exist_ok=True)

        with function_file.open("w", encoding="utf-8") as f:
            f.write(
                f"advancement revoke @s only "
                f"{namespace}:{relative_path.as_posix()}\n"
            )

        print(f"[完成] {recipe_id}")
        count += 1

    print("\n========== 生成完成 ==========")
    print(f"成功生成：{count}")
    print(f"跳过文件：{skipped}")
    print(f"Advancement目录：{advancement_dir}")
    print(f"Function目录：{function_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="根据配方原材料自动生成解锁进度"
    )

    parser.add_argument(
        "--recipes",
        default="data/mypack/recipe",
        help="配方文件夹"
    )

    parser.add_argument(
        "--advancements",
        default="data/mypack/advancement",
        help="进度输出文件夹"
    )

    parser.add_argument(
        "--functions",
        default="data/mypack/function",
        help="函数输出文件夹"
    )

    parser.add_argument(
        "--namespace",
        default=None,
        help="数据包命名空间，默认使用 recipe 文件夹的上级目录名称"
    )

    args = parser.parse_args()

    namespace = args.namespace or Path(args.recipes).parent.name

    generate(
        args.recipes,
        args.advancements,
        args.functions,
        namespace
    )
