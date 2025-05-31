from flask import Flask, request, jsonify
from flask_cors import CORS
import requests

app = Flask(__name__)
CORS(app)

# Basit veri deposu (geçici)
user_places = []
next_id = 1  # id için sayaç

@app.route("/api/user_places", methods=["POST"])
def add_user_place():
    data = request.get_json()
    user_places.append(data)
    return jsonify({"message": "Yer eklendi", "place": data})

@app.route("/api/user_places", methods=["GET"])
def get_user_places():
    return jsonify(user_places)

@app.route("/api/public_places", methods=["GET"])
def get_public_places():
    overpass_url = "http://overpass-api.de/api/interpreter"
    query = """
    [out:json][timeout:25];
    (
      node["wheelchair"="yes"](39.9,32.8,40.0,32.9);
    );
    out body;
    """
    try:
        response = requests.get(overpass_url, params={'data': query})
        data = response.json()

        places = []
        id_counter = 1
        for element in data.get("elements", []):
            name = element.get("tags", {}).get("name", "İsimsiz Mekan")
            lat = element.get("lat")
            lon = element.get("lon")
            places.append({
                "id": id_counter,
                "name": name,
                "lat": lat,
                "lon": lon,
                "accessible": True
            })
            id_counter += 1
        return jsonify(places)
    except Exception as e:
        return jsonify({"error": "Veri çekilemedi", "details": str(e)}), 500
    


if __name__ == "__main__":
    app.run(debug=True, port=5000)
